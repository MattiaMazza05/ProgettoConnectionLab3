package common;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class PuzzleRepository {
    private final List<PuzzleData.Puzzle> puzzles;
    private int currentIndex = 0; // prox puzzle da usare

    public PuzzleRepository(String filePath) throws IOException {
        Gson gson = new Gson();
        List<PuzzleData.Puzzle> loaded = new ArrayList<>();

        try (Reader reader = new FileReader(filePath)) {
            Type listType = new TypeToken<List<PuzzleData.Puzzle>>() {
            }.getType();
            List<PuzzleData.Puzzle> parsed = gson.fromJson(reader, listType);
            if (parsed != null) {
                loaded = parsed;
            }
        } catch (Exception e) {
            System.err.println("Errore caricamento puzzle: " + e.getMessage());
        }

        this.puzzles = loaded;
        System.out.println("Caricati " + puzzles.size() + " puzzle");
    }

    public synchronized PuzzleData.Puzzle getNextPuzzle() {
        if (currentIndex >= puzzles.size()) {
            return null;
        }
        return puzzles.get(currentIndex++);
    }

    public synchronized void setCurrentIndex(int index){
        if(index<0){
            currentIndex = 0;
        }else if(currentIndex > puzzles.size()){
            currentIndex = puzzles.size();
        }else{
            currentIndex = index;
        }
    }
}
