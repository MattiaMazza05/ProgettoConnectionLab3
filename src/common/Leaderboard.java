package common;

public class Leaderboard {
    public String username;
    public int score;

    public Leaderboard(String username, int score) { //questa classe mi serve solo per contenere le informazioni di username e score per calcolare la leaderboard in modo tale da non condividere tutte le altre informazioni come ad es. la password
        this.username = username;
        this.score = score;
    }
}
