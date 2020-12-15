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
		if(source.isCrashed() || target.isCrashed()) {
			return Color.GRAY;
		}
		
		return Color.GREEN;
    }
}
