package custom_style;

import java.awt.Color;
import java.awt.Font;

import org.apache.poi.hssf.util.HSSFColor.GREEN;

import agents.DiscretePropagation;
import agents.Participant;
import repast.simphony.visualizationOGL2D.DefaultStyleOGL2D;
import saf.v3d.scene.Position;

public class ParticipantStyle extends DefaultStyleOGL2D{
	@Override
    public Color getColor(Object o) {
		if(((Participant)o).getState() == Participant.AVAILABLE) 
			return Color.RED;
		else if(((Participant)o).getState() == Participant.SYN_RECEIVED
				|| ((Participant)o).getState() == Participant.SYN_SENT) 
			return Color.ORANGE;
		else if(((Participant)o).getState() == Participant.ESTABLISHED
				|| ((Participant)o).getState() == Participant.EXCHANGING_NEWS)
			return Color.GREEN;
		else if(((Participant)o).getState() == Participant.FINISHED)
			return Color.GRAY;
		
		return Color.BLUE;
    }
	
	@Override
	public String getLabel(Object o) {
		return ((Participant)o).getLabel();
	}
	
	@Override
	public Position getLabelPosition(Object object) {
		return Position.SOUTH;
	}
	
	@Override
	public Font getLabelFont(Object object) {
		return (new Font ("Arial", Font.PLAIN, 22));
	}
	
	
}