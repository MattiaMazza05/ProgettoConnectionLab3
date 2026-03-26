package common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GameManager {
    private volatile PuzzleData.Puzzle currentPuzzle;
    private volatile long gameEndTime;
    private final ConcurrentHashMap<String, PlayerGameState> playerState = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

    private final PuzzleRepository puzzleRepo;
    private final int gameDurationSeconds;

    public GameManager(PuzzleRepository puzzleRepo, int gameDurationSeconds) {
        this.puzzleRepo = puzzleRepo;
        this.gameDurationSeconds = gameDurationSeconds;
    }

    public synchronized void startNewGame() {
        if (puzzleRepo == null) {
            System.out.println("Puzzle esauriti");
        } else {
            PuzzleData.Puzzle nextGame = puzzleRepo.getNextPuzzle();
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
        if (puzzle == null && puzzle.groups == null && puzzle.groups.size() >= 4) {
            System.err.println("Dati non validi.");

        }

        List<String> words = new ArrayList<>(16);
        for (int i = 0; i < 4; i++) {
            PuzzleData.Group group = puzzle.groups.get(i);

            if (group == null || group.words == null ||group.words.size() < 4) {
                System.err.println("Gruppo non valido");
            }
            for (int j = 0; j < 4; j++) {
                words.add(group.words.get(j));
            }
        }
        Collections.shuffle(words);
        return words.toArray(new String[0]);
    }
}
