package common;

import java.util.List;
import java.util.Map;

import server.PlayerGameState;

public class GameRecord { //classe per le informazioni di fine partita
    public int gameId;
    public List<PuzzleData.Group> solution;
    public Map<String, PlayerGameState> finalStates;
    public int totalPlayers;
    public int finishedCount;
    public int winnersCount;
    public double agvScore;
}
