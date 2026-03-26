package common;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.lang.reflect.Type;

public class JsonPersistence { //salvataggio dati su file json
    public static synchronized void writeJson(String filePath, Object data) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create(); //metto setPrettyPrinting per avere il file users.json ben formattato
        String json = gson.toJson(data);
        try (FileWriter w = new FileWriter(filePath + ".tmp")) {
            w.append(json);
        } catch (Exception e) {
            System.out.println(e);
        }
        new File(filePath + ".tmp").renameTo(new File(filePath));
    }

    public static <T> T readJson(String filePath, Type type){
        if(new File(filePath).exists()){
            Gson gson = new Gson();
            try (FileReader fR = new FileReader(filePath);) {
                return gson.fromJson(fR, type);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
