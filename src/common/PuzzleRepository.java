package common;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import com.google.gson.Gson;

public class PuzzleRepository {
    private PuzzleData data;
    private int currentIndex = 0; // prox puzzle da usare

    public PuzzleRepository(String filePath) throws IOException {
        Gson gson = new Gson();
        try (Reader reader = new FileReader(filePath)) {
            this.data = gson.fromJson(reader, PuzzleData.class);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        System.out.println("Caricati " + data.puzzles.size() + "puzzle");
    }

    public synchronized PuzzleData.Puzzle getNextPuzzle() {
        if (currentIndex >= data.puzzles.size())
            return null;
        return data.puzzles.get(currentIndex++);
    }
}
