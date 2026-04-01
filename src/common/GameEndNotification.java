package common;

import java.util.List;

public class GameEndNotification {
    public String type = "gameId";
    public int gameId;
    public List<PuzzleData.Group> solution;
    public int totalPlayers;
    public int finishedPlayers;
    public int winnersCount;
    public double agvScore;
}
