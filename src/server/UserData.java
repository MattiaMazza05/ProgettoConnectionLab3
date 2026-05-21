package server;

public class UserData { //formato dati utente
    public String username;
    public String password;
    public int totalScore, gamesPlayed, gamesWon, currentStreak, maxStreak, perfectPuzzle;
    public int[] mistakeHistogram = new int[5];

    public UserData() {
    }

    public UserData(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
