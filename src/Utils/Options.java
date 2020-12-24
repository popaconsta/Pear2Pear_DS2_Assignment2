package Utils;

import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.parameter.Parameters;

public class Options {
	
	public static String PROTOCOL_VARIANT;
	public static double PROBABILITY_OF_EVENT;
	
	public static int MAX_PARTICIPANT_COUNT;
	public static int MAX_PROPAGATION_DISTANCE;
	public static String TOPOLOGY;
	public static int ENVIRONMENT_DIMENSION;
	public static double BANDWIDTH; 
	public static double EVENT_SIZE;
	public static double MAX_PROPAGATION_SPEED;
	public static double DELAY_PROBABILITY;
	public static double CRASH_PROBABILITY;
	public static double JOIN_PROBABILITY;
	public static double RELAY_RANGE;
	
	public static double PROBABILITY_TO_FOLLOW;
	public static double PROBABILITY_TO_BLOCK;
	
	
	public static int ACK_TIMEOUT;
	public static int FRONTIER_TIMEOUT;
	public static int NEWS_TIMEOUT;
	public static double PROBABILTY_OF_HANDSHAKE;
	
	public static void load() {
		Parameters params = RunEnvironment.getInstance().getParameters();
		
		PROBABILITY_OF_EVENT = params.getDouble("PROBABILITY_OF_EVENT"); 
		MAX_PARTICIPANT_COUNT = params.getInteger("PARTICIPANT_COUNT");
		TOPOLOGY = params.getString("TOPOLOGY");
		ENVIRONMENT_DIMENSION = params.getInteger("ENVIRONMENT_DIMENSION");
		BANDWIDTH =  params.getInteger("BANDWIDTH");
		EVENT_SIZE = params.getDouble("EVENT_SIZE");
		DELAY_PROBABILITY = params.getDouble("DELAY_PROBABILITY"); //TODO: remove?
		CRASH_PROBABILITY = params.getDouble("CRASH_PROBABILITY");
		JOIN_PROBABILITY = params.getDouble("JOIN_PROBABILITY");
		PROBABILITY_TO_FOLLOW = params.getDouble("PROBABILITY_TO_FOLLOW");
		PROBABILITY_TO_BLOCK = params.getDouble("PROBABILITY_TO_BLOCK");
		PROTOCOL_VARIANT = params.getString("PROTOCOL_VARIANT");
		ACK_TIMEOUT = params.getInteger("ACK_TIMEOUT");
		FRONTIER_TIMEOUT = params.getInteger("FRONTIER_TIMEOUT");
		NEWS_TIMEOUT = params.getInteger("NEWS_TIMEOUT");
		PROBABILTY_OF_HANDSHAKE = params.getDouble("PROBABILITY_OF_HANDSHAKE");
	}
}
