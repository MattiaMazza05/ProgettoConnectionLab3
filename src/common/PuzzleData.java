package common;

import java.util.List;

public class PuzzleData {
    public List<Puzzle> puzzles;

    public static class Puzzle {
        public int gameId;
        public List<Group> groups;
    }

    public static class Group {
        public String theme;
        public List<String> words;
    }
}
