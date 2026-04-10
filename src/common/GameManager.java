package common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import server.ClientSession;

import java.util.Set;

public class GameManager {
    private volatile PuzzleData.Puzzle currentPuzzle; // uso volatile per ottenere sempre la variabile più aggiornata
    private volatile long gameEndTime;
    private final ConcurrentHashMap<String, PlayerGameState> playerState = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final UserManager userManager;
    private final PuzzleRepository puzzleRepo;
    private final int gameDurationSeconds;
    private final UdpNotifier udpNotifier;
    private final List<GameRecord> gameHistory = new ArrayList<>();

    public GameManager(PuzzleRepository puzzleRepo, int gameDurationSeconds, UserManager userManager,
            UdpNotifier udpNotifier) {
        this.puzzleRepo = puzzleRepo;
        this.gameDurationSeconds = gameDurationSeconds;
        this.userManager = userManager;
        this.udpNotifier = udpNotifier;

    }

    public synchronized void startNewGame() {
        if (puzzleRepo == null) {
            System.out.println("Puzzle esauriti");
        } else {
            PuzzleData.Puzzle nextGame = puzzleRepo.getNextPuzzle();
            currentPuzzle = nextGame;
            if (nextGame == null) {
                System.out.println("Puzzle esauriti.");
                return;
            }
            playerState.clear();
            gameEndTime = System.currentTimeMillis() + (gameDurationSeconds * 1000L);
            System.out.println("Nuova partita avviata!");
            timer.schedule(this::onGameEnd, gameDurationSeconds, TimeUnit.SECONDS);
        }
    }

    private synchronized void onGameEnd() {
        GameEndNotification gameNotification = new GameEndNotification();
        gameNotification.solution = buildSolution();
        int totalPlayers = 0, finishedPlayers = 0, winnersCount = 0, sumScore = 0;
        for (java.util.Map.Entry<String, PlayerGameState> entry : playerState.entrySet()) {
            String username = entry.getKey();
            PlayerGameState state = entry.getValue();

            totalPlayers++;
            sumScore += state.score;

            if (state.finished) {
                finishedPlayers++;
            } else {
                state.finished = true;
                userManager.recordGameResult(username, false, state.mistakes, state.score);
                finishedPlayers++;
            }

            if (state.correctGroups != null && state.correctGroups.size() == 3) {
                winnersCount++;
            }
        }
        gameNotification.totalPlayers = totalPlayers;
        gameNotification.finishedPlayers = finishedPlayers;
        gameNotification.winnersCount = winnersCount;
        if (totalPlayers > 0) {
            gameNotification.agvScore = sumScore / totalPlayers;
        }
        gameNotification.solution = currentPuzzle.groups;

        if (currentPuzzle != null) { // creo e salvo game record
            GameRecord record = new GameRecord();
            record.gameId = currentPuzzle.gameId;
            record.solution = currentPuzzle.groups;
            record.finalStates = new ConcurrentHashMap<>(playerState);
            record.finishedCount = finishedPlayers;
            record.winnersCount = winnersCount;
            record.agvScore = gameNotification.agvScore;
            record.date = new java.util.Date().toString();
            gameHistory.add(record);
        }

        List<ClientSession> sessions = userManager.getSessions();
        try {
            udpNotifier.notifyAllPlayer(sessions, gameNotification);
        } catch (Exception e) {
            System.err.println("Errore nell'invio di notifiche UDP: " + e.getMessage());
        }
        startNewGame();
    }

    public String[] getShuffledWords() {
        PuzzleData.Puzzle puzzle = currentPuzzle;
        if (puzzle == null || puzzle.groups == null || puzzle.groups.size() < 4) {
            System.err.println("Dati non validi.");
            return new String[0];

        }

        List<String> words = new ArrayList<>(16);
        for (int i = 0; i < 4; i++) {
            PuzzleData.Group group = puzzle.groups.get(i);

            if (group == null || group.words == null || group.words.size() < 4) {
                System.err.println("Gruppo non valido");
            }
            for (int j = 0; j < 4; j++) {
                words.add(group.words.get(j));
            }
        }
        Collections.shuffle(words);
        return words.toArray(new String[0]);
    }

    private PuzzleData.Group findMatchingGroup(List<String> words, PlayerGameState state) {
        if (currentPuzzle == null || state == null || words == null || words.size() != 4) {
            return null;
        }
        Set<String> clientProposal = new HashSet<>(words);
        for (int i = 0; i < (currentPuzzle.groups).size(); i++) {
            PuzzleData.Group group = currentPuzzle.groups.get(i);
            if (group == null || group.words == null || group.words.size() != 4) {
                continue;
            }
            Set<String> correctWords = new HashSet<>(group.words);
            if (clientProposal.equals(correctWords)) {
                boolean alreadyFound = false;
                for (int j = 0; j < state.correctGroups.size(); j++) {
                    Set<String> groupCheck = new HashSet<>(state.correctGroups.get(j).words);
                    if (groupCheck.equals(correctWords)) {
                        alreadyFound = true;
                        break;
                    }
                }
                if (!alreadyFound) {
                    return group;
                }
            }
        }
        return null;
    }

    public synchronized ServerResponse submitProposal(String username, List<String> words) {
        PlayerGameState state = playerState.get(username);
        if (state == null) {
            return ServerResponse.err("Non stai partecipando alla partita.");
        }
        if (state.finished == true) {
            return ServerResponse.err("Partita terminata.");
        }
        if (words == null || words.size() != 4) {
            return ServerResponse.err("Proposta malformata: scrivi esattamente 4 parole.");
        }
        Set<String> wordsInGame = new HashSet<>();
        if (currentPuzzle == null || currentPuzzle.groups == null) {
            return ServerResponse.err("Nessuna partita attiva.");
        }
        for (int i = 0; i < currentPuzzle.groups.size(); i++) {
            PuzzleData.Group group = currentPuzzle.groups.get(i);
            if (group != null && group.words != null) {
                wordsInGame.addAll(group.words);
            }
        }
        Set<String> noDuplicates = new HashSet<>(words);// uso un set per evitare che l'utene usi parole duplicate
        if (noDuplicates.size() != 4) {
            return ServerResponse.err("Proposta malformata: parole duplicate.");
        }
        if (!wordsInGame.containsAll(noDuplicates)) {
            return ServerResponse.err("Proposta malformata: parole non presenti nel puzzle.");
        }
        if (state.mistakes >= 4) {
            return ServerResponse.err("Hai commesso 4 errori, tentativi esauriti.");
        }
        if (System.currentTimeMillis() > gameEndTime) {
            return ServerResponse.err("Tempo esaurito.");
        }
        List<String> wordsToCheck = new ArrayList<>(noDuplicates);
        PuzzleData.Group matchedGroup = findMatchingGroup(wordsToCheck, state);
        if (matchedGroup == null) {
            state.mistakes++;
            if (state.mistakes == 4) {
                state.finished = true;
                state.score = (state.correctGroups.size() * 6) - (state.mistakes * 4);
                userManager.recordGameResult(username, false, state.mistakes, state.score);
            } else {
                state.score = (state.correctGroups.size() * 6) - (state.mistakes * 4);
            }
            ProposalResponse response = new ProposalResponse("error", "Proposta errata");
            response.mistakes = state.mistakes;
            response.score = state.score;
            return response;
        } else {
            state.correctGroups.add(matchedGroup);
            int bonusGroup = Math.min(state.correctGroups.size(), 3);
            int bonus = bonusGroup * 6;
            state.score = bonus - (state.mistakes * 4);
            if (state.correctGroups.size() == 3) {
                state.finished = true;
                userManager.recordGameResult(username, true, state.mistakes, state.score);
            }

            ProposalResponse response = new ProposalResponse("ok", "Corretto Tema: " + matchedGroup.theme);
            response.groupFound = matchedGroup;
            response.score = state.score;
            response.mistakes = state.mistakes;
            response.won = state.finished;
            return response;
        }
    }

    public void joinCurrentGame(String username) {
        playerState.putIfAbsent(username, new PlayerGameState());
    }

    public List<PuzzleData.Group> buildSolution() {
        if (currentPuzzle == null) {
            return null;
        }
        return currentPuzzle.groups;
    }

    private GameRecord getHistoricalgame(int gameId) {
        for (GameRecord r : gameHistory) {
            if (r.gameId == gameId) {
                return r;
            }
        }
        return null;
    }

    public JsonObject requestGameInfo(String username, int gameId) {
        Gson gson = new Gson();
        JsonObject r = new JsonObject();

        if (gameId == -1 || gameId == currentPuzzle.gameId) { // uso -1 per indicare che l'utente vuole le statistiche
                                                              // della partita corrente, dato che i game iniziano da 0
            PlayerGameState state = playerState.get(username);

            if (state == null) {
                r.addProperty("status", "error");
                r.addProperty("message", "Non stai partecipando a questa partita");
                return r;
            }

            if (System.currentTimeMillis() < gameEndTime && !state.finished) {
                r.addProperty("status", "ongoing");
                r.addProperty("timeRemaining", (gameEndTime - System.currentTimeMillis()) / 1000);
                r.addProperty("score", state.score);
                r.addProperty("mistakes", state.mistakes);
                List<String> remainingWords = new ArrayList<>();
                Collections.addAll(remainingWords, getShuffledWords());
                for (PuzzleData.Group g : state.correctGroups) {
                    remainingWords.removeAll(g.words);
                }
                r.add("remainingWords", gson.toJsonTree(remainingWords));
                r.add("correctGroups", gson.toJsonTree(state.correctGroups));
                return r;
            } else {
                r.addProperty("status", "finished");
                r.add("solution", gson.toJsonTree(currentPuzzle.groups));
                r.addProperty("correctCount", state.correctGroups.size());
                r.addProperty("mistakes", state.mistakes);
                r.addProperty("score", state.score);
                return r;
            }
        }
        GameRecord record = getHistoricalgame(gameId);

        if (record == null) {
            r.addProperty("status", "error");
            r.addProperty("message", "Partita non trovata.");
            return r;
        }

        // Continua con il resto del tuo codice...
        PlayerGameState oldState = record.finalStates.get(username);
        if (oldState == null) {
            r.addProperty("status", "error");
            r.addProperty("message", "Non hai partecipato a questa partita.");
            return r;
        }

        r.addProperty("status", "finished");
        r.add("solution", gson.toJsonTree(record.solution));
        r.addProperty("correctCount", oldState.correctGroups.size());
        r.addProperty("mistakes", oldState.mistakes);
        r.addProperty("score", oldState.score);
        return r;
    }
}
