package common;

import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.reflect.TypeToken;

import server.ClientSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class UserManager { // classe per la gestione degli utenti e autenticazione
    public ConcurrentHashMap<String, UserData> users = new ConcurrentHashMap<>();
    private final Set<String> loggedIn = ConcurrentHashMap.newKeySet();
    private final Set<ClientSession> activeSession = ConcurrentHashMap.newKeySet();

    public synchronized void load() {
        try {
            Type usersType = new TypeToken<ConcurrentHashMap<String, UserData>>() {
            }.getType();
            ConcurrentHashMap<String, UserData> loaded = JsonPersistence.readJson("users.json", usersType);
            if (loaded != null)
                users = loaded;

        } catch (Exception e) {
            System.err.println(e);
        }

    }

    public synchronized void save() {
        JsonPersistence.writeJson("users.json", users);
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

    public synchronized void logout(ClientSession session) {
        if (session.LoggedUsername != null) {
            loggedIn.remove(session.LoggedUsername);
            session.LoggedUsername = null;
        }
    }

    public void recordGameWin(String username, int mistakes) {
        System.out.println("Partite vinte: Test");
    }

    public int recordGameLoss(String username, int mistakes) {
        System.out.println("Partite perse: test");
        return 1;
    }

    public void addSession(ClientSession session){
        if (session != null && session.LoggedUsername != null) {
            activeSession.add(session);
        }
    }
    public void removeSession(ClientSession session){
        activeSession.remove(session);
    } 

    public List<ClientSession> getSessions(){//crea una copia e ritorna un array delle sessioni attive
        return new ArrayList<>(activeSession);
    }
}
