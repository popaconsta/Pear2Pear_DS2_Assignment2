package custom_style;

import java.awt.Color;

import agents.Participant;
import repast.simphony.relogo.BaseLink;
import repast.simphony.space.graph.RepastEdge;
import repast.simphony.visualizationOGL2D.DefaultEdgeStyleOGL2D;

public class EdgeStyle extends DefaultEdgeStyleOGL2D{
	public Color getColor(RepastEdge<?> edge) {
		Participant source = (Participant) edge.getSource();
		Participant target = (Participant) edge.getTarget();
		
		if((source.getState() == Participant.SYN_SENT && source.getCurrentPeer().equals(target))
				|| (target.getState() == Participant.SYN_SENT && target.getCurrentPeer().equals(source))) {
			
			return Color.RED;
		} else if((source.getState() == Participant.SYN_RECEIVED && source.getCurrentPeer().equals(target))
				|| (target.getState() == Participant.SYN_RECEIVED && target.getCurrentPeer().equals(source))) {
			
			return Color.ORANGE;
		} else if((source.getState() == Participant.ESTABLISHED && source.getCurrentPeer().equals(target))
				|| (target.getState() == Participant.ESTABLISHED && target.getCurrentPeer().equals(source))
				|| (source.getState() == Participant.EXCHANGING_NEWS && source.getCurrentPeer().equals(target))
				|| (target.getState() == Participant.EXCHANGING_NEWS && target.getCurrentPeer().equals(source))) {

			return Color.GREEN;
		} else if((source.getState() == Participant.FINISHED && source.getCurrentPeer().equals(target))
				|| (target.getState() == Participant.FINISHED && target.getCurrentPeer().equals(source))) {
			
			return Color.GRAY;
		}
		
		return Color.BLUE;
    }
}
