package common;

import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.reflect.TypeToken;

public class UserManager {
    public ConcurrentHashMap<String, UserData> users = new ConcurrentHashMap<>();

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
}
