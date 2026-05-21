package server;

import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import common.JsonPersistence;
import common.ServerResponse;
import common.Leaderboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class UserManager { // classe per la gestione degli utenti e autenticazione 
    public ConcurrentHashMap<String, UserData> users = new ConcurrentHashMap<>();
    private final Set<String> loggedIn = ConcurrentHashMap.newKeySet();
    private final Set<ClientSession> activeSession = ConcurrentHashMap.newKeySet();

    private final String usersFilePath;

    public UserManager(String usersFilePath) {
        this.usersFilePath = usersFilePath;
    }

    public synchronized void load() {
        try {
            Type usersType = new TypeToken<ConcurrentHashMap<String, UserData>>() {
            }.getType();
            ConcurrentHashMap<String, UserData> loaded = JsonPersistence.readJson(usersFilePath, usersType);
            if (loaded != null)
                users = loaded;

        } catch (Exception e) {
            System.err.println(e);
        }

    }

    public synchronized void save() {
        JsonPersistence.writeJson(usersFilePath, users);
        System.out.println("Salvataggio su file eseguito!");
    }

    public synchronized boolean register(String username, String password) {
        if (users.containsKey(username)) {
            return false;
        } else {
            UserData utente = new UserData(username, password);
            users.put(username, utente);
            save();
            return true;
        }
    }

    public synchronized ServerResponse login(String username, String password) {
        if (users.containsKey(username)) {
            UserData user = users.get(username);
            if (user.password.equals(password)) {
                if (!loggedIn.contains(username)) {
                    loggedIn.add(username);
                    return ServerResponse.ok("Utente Loggato");
                } else {
                    return ServerResponse.err("Utente già Loggato");
                }
            } else {
                return ServerResponse.err("Password Errata");
            }
        } else {
            return ServerResponse.err("Utente non trovato");
        }
    }

    public synchronized void logout(ClientSession session) {// se l'utente è loggato lo rimuovo dalla lista e imposto a null loggedUsername
        if (session.LoggedUsername != null) {
            loggedIn.remove(session.LoggedUsername);
            session.LoggedUsername = null;
        }
    }

    public synchronized void recordGameResult(String username, boolean won, int mistakes, int score) {
        UserData u = users.get(username);
        if (u == null) {
            return;
        }
        u.gamesPlayed++;
        u.totalScore += score;
        u.mistakeHistogram[mistakes]++;

        if (won) {
            u.gamesWon++;
            u.currentStreak++;
            u.maxStreak = Math.max(u.maxStreak, u.currentStreak);
            if (mistakes == 0) {
                u.perfectPuzzle++;
            }
        } else {
            u.currentStreak = 0;
        }
    }

    public void addSession(ClientSession session) {
        if (session != null && session.LoggedUsername != null) {
            activeSession.add(session);
        }
    }

    public void removeSession(ClientSession session) {//rimuovo dalle sessioni attive
        activeSession.remove(session);
    }

    public List<ClientSession> getSessions() {// crea una copia e ritorna un array delle sessioni attive
        return new ArrayList<>(activeSession);
    }

    public List<Leaderboard> getLeaderboard() {
        List<Leaderboard> lista = new ArrayList<>();
        for (UserData u : users.values()) {
            Leaderboard lb = new Leaderboard(u.username, u.totalScore); // creo una lista contenete per ogni utente solo
                                                                        // i campi che mi servono per costrure la
                                                                        // leaderbord
            lista.add(lb);
        }
        lista.sort((a, b) -> Integer.compare(b.score, a.score)); // ordino in modo decrescente la lista e la ritorno
        return lista;
    }

    public synchronized ServerResponse updateCredentials(String oldName, String oldPsw, String newName, String newPsw) {
        UserData user = users.get(oldName);
        if (user == null) {
            return ServerResponse.err("Utente non trovato.");
        }
        if (!user.password.equals(oldPsw)) {
            return ServerResponse.err("Password errata.");
        }
        if (newPsw != null && !newPsw.isBlank()) {
            user.password = newPsw;
        }
        if (newName != null && !newName.isBlank() && !newName.equals(oldName)) {
            if (users.containsKey(newName)) {// se viene usato un nome utente gia registrato lo segnalo
                return ServerResponse.err("Nome utente già esistente, scagliene uno diverso.");
            }
            users.remove(oldName);
            user.username = newName;
            users.put(newName, user);
            if (loggedIn.remove(oldName)) {
                loggedIn.add(newName);
            }
        }
        return ServerResponse.ok("Credenziali aggiornate con successo");
    }

    public JsonObject requestPlayerStats(String username) {
        JsonObject r = new JsonObject();
        UserData u = users.get(username);
        if (u == null) {
            r.addProperty("status", "error");
            r.addProperty("message", "Utente non trovato");
            return r;
        }
        r.addProperty("status", "ok");
        r.addProperty("puzzleCompleted", u.gamesPlayed);
        double winRate = 0.0, lossRate = 0.0;
        if (u.gamesPlayed > 0) {
            winRate = ((double) u.gamesWon / u.gamesPlayed) * 100;
            int losses = u.gamesPlayed - u.gamesWon;
            lossRate = ((double) losses / u.gamesPlayed) * 100;
        }
        r.addProperty("winRate", winRate);
        r.addProperty("lossRate", lossRate);
        r.addProperty("currentStreak", u.currentStreak);
        r.addProperty("maxStreak", u.maxStreak);
        r.addProperty("perfectPuzzle", u.perfectPuzzle);
        JsonArray hist = new JsonArray();
        for (int val : u.mistakeHistogram) {
            hist.add(val);
        }
        r.add("mistakeHistogram", hist);
        return r;
    }

    public synchronized List<String> getLoggedUsers(){
        return new ArrayList<>(loggedIn);
    }
}
