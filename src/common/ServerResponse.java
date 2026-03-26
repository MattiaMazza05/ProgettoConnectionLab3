package common;

public class ServerResponse {// questa classe mi serve per rispondere al client, ogni volta che devo mandare
                             // un messaggio di risposta creo un oggetto di tipo ServerReponse che passo a
                             // Gson per trasformarlo in json
    public String status; // ok o error
    public String message;

    public ServerResponse(String status, String message) {
        this.status = status;
        this.message = message;
    }

    public static ServerResponse ok (String message){
        return new ServerResponse("ok", message);
    }

    public static ServerResponse err (String message){
        return new ServerResponse("error", message);
    }
}
