package common;

import java.util.ArrayList;
import java.util.List;

public class PlayerGameState {
    public int mistakes = 0;
    public List<PuzzleData.Group> correctGroups = new ArrayList<>();
    public int score = 0;
    public boolean finished = false;
    public boolean online = true;

    public PlayerGameState() {

    }

    public PlayerGameState(int mistakes, List<PuzzleData.Group> correctGroups, int score, boolean finished) {
        this.mistakes = mistakes;
        this.correctGroups = correctGroups;
        this.score = score;
        this.finished = finished;
    }
}
