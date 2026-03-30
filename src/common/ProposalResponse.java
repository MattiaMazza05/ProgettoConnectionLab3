package common;

public class ProposalResponse extends ServerResponse{
    public int score;
    public int mistakes;
    public boolean won = false;
    public PuzzleData.Group groupFound = null;

    public ProposalResponse(String status, String message){
        super(status, message);
    }
}
