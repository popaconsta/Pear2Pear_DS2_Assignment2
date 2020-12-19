package custom_style;

import java.awt.Color;

import agents.Participant;
import agents.Participant.State;
import repast.simphony.relogo.BaseLink;
import repast.simphony.space.graph.RepastEdge;
import repast.simphony.visualizationOGL2D.DefaultEdgeStyleOGL2D;

public class EdgeStyle extends DefaultEdgeStyleOGL2D{
	public Color getColor(RepastEdge<?> edge) {
		Participant source = (Participant) edge.getSource();
		Participant target = (Participant) edge.getTarget();
		
		if((source.getState() == State.SYN_SENT && source.getCurrentPeer().equals(target))
				|| (target.getState() == State.SYN_SENT && target.getCurrentPeer().equals(source))) {
			
			return Color.RED;
		} else if((source.getState() == State.SYN_RECEIVED && source.getCurrentPeer().equals(target))
				|| (target.getState() == State.SYN_RECEIVED && target.getCurrentPeer().equals(source))) {
			
			return Color.ORANGE;
		} else if((source.getState() == State.CONN_ESTABLISHED && source.getCurrentPeer().equals(target))
				|| (target.getState() == State.CONN_ESTABLISHED && target.getCurrentPeer().equals(source))
				|| (source.getState() == State.NEWS_EXCHANGED && source.getCurrentPeer().equals(target))
				|| (target.getState() == State.NEWS_EXCHANGED && target.getCurrentPeer().equals(source))) {

			return Color.GREEN;
		} else if((source.getState() == State.FINISHED && source.getCurrentPeer().equals(target))
				|| (target.getState() == State.FINISHED && target.getCurrentPeer().equals(source))) {
			
			return Color.GRAY;
		}
		
		return Color.BLUE;
    }
}
