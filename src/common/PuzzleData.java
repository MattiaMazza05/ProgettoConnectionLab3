package common;

import java.util.List;

public class PuzzleData {
    public List<Puzzle> puzzles;

    public static class Puzzle {
        public int id;
        public String date;
        public List<Group> groups;
    }

    public static class Group {
        public String theme;
        public String color;
        public List<String> words;
    }
}
