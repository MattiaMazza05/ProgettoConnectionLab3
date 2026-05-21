package common;

import java.util.List;
import java.util.Map;

import server.PlayerGameState;

public class GameRecord {
    public int gameId;
    public List<PuzzleData.Group> solution;
    public Map<String, PlayerGameState> finalStates;
    public int totalPlayers;
    public int finishedCount;
    public int winnersCount;
    public double agvScore;
}
