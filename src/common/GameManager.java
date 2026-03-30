package common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Set;

public class GameManager {
    private volatile PuzzleData.Puzzle currentPuzzle;
    private volatile long gameEndTime;
    private final ConcurrentHashMap<String, PlayerGameState> playerState = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final UserManager userManager;

    private final PuzzleRepository puzzleRepo;
    private final int gameDurationSeconds;

    public GameManager(PuzzleRepository puzzleRepo, int gameDurationSeconds, UserManager userManager) {
        this.puzzleRepo = puzzleRepo;
        this.gameDurationSeconds = gameDurationSeconds;
        this.userManager = userManager;
    }

    public synchronized void startNewGame() {
        if (puzzleRepo == null) {
            System.out.println("Puzzle esauriti");
        } else {
            PuzzleData.Puzzle nextGame = puzzleRepo.getNextPuzzle();
            currentPuzzle = nextGame;
            if (nextGame == null) {
                System.out.println("Puzzle esauriti.");
            }
            playerState.clear();
            gameEndTime = System.currentTimeMillis() + (gameDurationSeconds * 1000L);
            System.out.println("Nuova partita avviata!");
            timer.schedule(this::onGameEnd, gameDurationSeconds, TimeUnit.SECONDS);
        }
    }

    private void onGameEnd() {
        System.out.println("Partita terminata");
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
        if (state.mistakes >= 4) {
            return ServerResponse.err("Hai commesso 4 errori, tentativi esauriti.");
        }
        if (System.currentTimeMillis() > gameEndTime) {
            return ServerResponse.err("Tempo esaurito.");
        }
        if (words == null || words.size() != 4) {
            return ServerResponse.err("Proposta malformata: scrivi esattamente 4 parole.");
        }
        Set<String> noDuplicates = new HashSet<>(words);// uso un set per evitare che l'utene usi parole duplicate
        if (noDuplicates.size() != 4) {
            return ServerResponse.err("Proposta malformata: parole duplicate.");
        }
        List<String> wordsToCheck = new ArrayList<>(noDuplicates);
        PuzzleData.Group matchedGroup = findMatchingGroup(wordsToCheck, state);
        if (matchedGroup == null) {
            state.mistakes++;
            if (state.mistakes == 4) {
                state.finished = true;
                state.score = (state.correctGroups.size() * 6)
                        - (state.mistakes * userManager.recordGameLoss(username, state.mistakes));
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
            if(state.correctGroups.size() == 3 ){
                state.finished = true;
                userManager.recordGameWin(username, state.mistakes);
            }

            ProposalResponse response = new ProposalResponse("ok", "Corretto Tema: " + matchedGroup.theme);
            response.groupFound = matchedGroup;
            response.score = state.score;
            response.mistakes = state.mistakes;
            response.won = state.finished;
            return response;
        }
    }
}
