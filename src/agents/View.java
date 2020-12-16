package agents;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;


/*
 * This class holds a partial view of the latest participants
 * that have exchanged information with a given participant
 */
public class View extends ArrayList<Participant> {

    private int maxSize;

    public View(int size){
        this.maxSize = size;
    }

    public boolean add(Participant p){
        boolean r = super.add(p);
        if (size() > maxSize){
            removeRange(0, size() - maxSize);
        }
        return r;
    }

    public Participant getYoungest() {
        return get(size() - 1);
    }

    public Participant getOldest() {
        return get(0);
    }
    
    public Participant getRandom() {
    	int randomIndex = ThreadLocalRandom.current().nextInt(0, size());
    	return get(randomIndex);
    }
}