package pear2Pear_DS2_Assignment2;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.print.attribute.HashAttributeSet;

import Utils.DataCollector;
import Utils.Options;
import agents.Participant;
import agents.View;
import repast.simphony.context.Context;
import repast.simphony.context.space.continuous.ContinuousSpaceFactory;
import repast.simphony.context.space.continuous.ContinuousSpaceFactoryFinder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.NdPoint;
import repast.simphony.space.continuous.RandomCartesianAdder;
import repast.simphony.space.continuous.SimpleCartesianAdder;
import repast.simphony.space.graph.Network;
import repast.simphony.space.graph.RepastEdge;
import repast.simphony.util.ContextUtils;
import security.KeyManager;


public class TopologyManager {
	
	private static List<NdPoint> availableLocations = null; //available locations i.e crashed nodes locations
	private static ContinuousSpace<Object> space = null; //the space where participants are placed
	private static Context<Object> context; //simulation context
	private static int nextId; //next available participant id
	private static int currentPeerNum; //currently alive participants number
	private static CopyOnWriteArrayList<Participant> currentParticipants; //list of alive participants
	private static Map<PublicKey, Participant> allTimeParticipants; // list of participants that have been alive during simulaion
	private static Map<PublicKey, Integer> globalFrontier;
//	private static int overallFailedConnections;
//	private static int overallSucceededConnections;
//	private static int overallNumberOfNews;
//	private static int overallSummationNews;
//	private static double overallPercentage;
//	private static int updatedNodes;
//	private static int overallNumberOfEdges;

    // static method to initialize the topology manager
    public static void initialize(Context<Object> ctx) { 
    	int tickCount = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
    	availableLocations = new ArrayList<>(); //instantiate the list
    	currentParticipants = new CopyOnWriteArrayList<Participant>();
    	allTimeParticipants = new HashMap<>();
//    	overallFailedConnections = 0;
//    	overallSucceededConnections = 0;
//    	overallNumberOfNews = 0;
//    	overallSummationNews = 0;
//    	overallPercentage = 0;
//    	updatedNodes = 0;
//    	overallNumberOfEdges = 0;
    	
    	context = ctx;
    	
    	//Prepare the space for the participants
		ContinuousSpaceFactory spaceFactory =
				ContinuousSpaceFactoryFinder.createContinuousSpaceFactory(null);
		
		/*
		 * O - ring
		 * * - (extended) star (please note this is not a comment mistake, the "*" symbol is used as an option
		 * R - Random
		 * | - Line
		 */	 
		String topology = Options.TOPOLOGY;
		
		//Instantiate space based on topology
		if(topology.compareTo("R") == 0) {
			space = spaceFactory.createContinuousSpace(
					"space", context,
					new RandomCartesianAdder<Object>(), //random location
					new repast.simphony.space.continuous.StrictBorders(), 
					Options.ENVIRONMENT_DIMENSION, Options.ENVIRONMENT_DIMENSION
					);
		} else {
			space = spaceFactory.createContinuousSpace(
					"space", context,
					new SimpleCartesianAdder<Object>(), //location has still to be decided in this case
					new repast.simphony.space.continuous.StrictBorders(), 
					Options.ENVIRONMENT_DIMENSION, Options.ENVIRONMENT_DIMENSION
					);
		}
		
		//Create and add the participants to the space
		for (int i = 0; i < Options.MAX_PARTICIPANT_COUNT; i++) {
			Participant p = new Participant(KeyManager.PUBLIC_KEYS.get(i), KeyManager.PRIVATE_KEYS.get(i), Integer.toString(i));
			currentParticipants.add(p);
			allTimeParticipants.put(p.getId(), p);
		}
		//Initialize participants view
		for (int i = 0; i < Options.MAX_PARTICIPANT_COUNT; i++) {
			context.add(currentParticipants.get(i));
			View view = new View(Integer.toString(i), Options.MAX_PARTICIPANT_COUNT - 1);
			for(int j = 0; j < Options.MAX_PARTICIPANT_COUNT - 1; j++) 
				view.add(currentParticipants.get((i + j + 1) % Options.MAX_PARTICIPANT_COUNT), 0);
			currentParticipants.get(i).setView(view);
		}
    	nextId = Options.MAX_PARTICIPANT_COUNT; //initially the next id is the initial amount of participants
    	currentPeerNum = Options.MAX_PARTICIPANT_COUNT;
     
    }
    
    // Translate id to label
    public static Participant getParticipantById(PublicKey id) {
    	return allTimeParticipants.get(id);
    }
	
    
	public static void removeParticipant(Participant participant) {
		availableLocations.add(space.getLocation(participant)); //mark the location of the crashed node as available
		currentPeerNum--;
		
		//Remove participant from list
		for(Participant p : currentParticipants)
			if(p.equals(participant))
				currentParticipants.remove(p);
		
		System.out.println(participant.getLabel() + " is still alive");
		
		//Remove the edges which are connected to the crashed node
		Network<Object> net = (Network<Object>) context.getProjection("peer network"); 
		CopyOnWriteArrayList<RepastEdge<Object>> edges = new CopyOnWriteArrayList<>(); //thread-safe method
		net.getOutEdges(participant).forEach(edges::add);
		//Iterate through edges and remove them
		for(RepastEdge<Object> edge : edges) {
			net.removeEdge(edge);
		}
		//Finally remove the participant
		context.remove(participant);
		
	}
	
	public static void addEdge(Participant source, Participant target) {
		Network<Object> net = (Network<Object>) context.getProjection("peer network");	
		
		if(!source.isCrashed() && !target.isCrashed())
			net.addEdge(source, target);
	}
	
	public static void removeEdge(Participant source, Participant target) {
		Network<Object> net = (Network<Object>) context.getProjection("peer network");	
		
		if(!source.isCrashed() && !target.isCrashed())
			net.removeEdge(net.getEdge(source, target));
	}
	
	/*
	 * Since each participant has a given probability of crashing, the expected
	 * value of crashed relay per tick is equal to participant_number * prob_crash
	 * In order to compensate the number of crashed relays, we need to run
	 * this method a number of times, each time having a small probability
	 * to add a new participant to the context.
	 */
	@ScheduledMethod(start = 1, interval = 1)
	public static void addNewParticipants() {
		for(int i=0; i<Options.MAX_PARTICIPANT_COUNT; i++) {
			double coinToss = RandomHelper.nextDoubleFromTo(0, 1);
			//Check if there are available locations
			if(coinToss <= Options.JOIN_PROBABILITY && availableLocations.size() > 0) {
				double x = -1, y = -1;
				//Pick a random locations among the available ones
				if(Options.TOPOLOGY.compareTo("R") == 0) {
					 x = RandomHelper.nextDoubleFromTo(1, Options.ENVIRONMENT_DIMENSION-1);
					 y = RandomHelper.nextDoubleFromTo(1, Options.ENVIRONMENT_DIMENSION-1);
				} else {
					int index = RandomHelper.nextIntFromTo(0, availableLocations.size()-1);
					NdPoint spacePt = availableLocations.get(index);
					availableLocations.remove(spacePt);
					x = spacePt.getX();
					y = spacePt.getY();
				}
				
				//Generate private and public keys for the new relay
				KeyManager.generateKeys(nextId);
				System.out.println("Participant(" + nextId + ") is joining the context");
				
				//Prepare participant view
				View view = new View(Integer.toString(nextId), currentPeerNum);
				List<Participant> randomPeers = new ArrayList<>(currentParticipants);
				Collections.shuffle(randomPeers);
				for(int j=0; j<currentPeerNum; j++) {
					Participant p = randomPeers.get(j);
					int tickCount = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
					if(p != null)
						view.add(p, tickCount);
				}
				
				//Place the new participant in the context
				Participant participant = new Participant(
						KeyManager.PUBLIC_KEYS.get(nextId), KeyManager.PRIVATE_KEYS.get(nextId), Integer.toString(nextId));
				participant.setView(view);
				
				currentParticipants.add(participant);
				allTimeParticipants.put(participant.getId(), participant);
				
				nextId++;
				context.add(participant);
				space.moveTo(participant, x, y);
				currentPeerNum++;
			}
		}
	}
	
	//Position the participants in a structured way, in order to form a specific topology
	public static void buildTopology() {
		int n = Options.MAX_PARTICIPANT_COUNT;
		String topology = Options.TOPOLOGY;
		
		//Ring topology
		if(topology.compareTo("O") == 0) {
			double radius = (Options.ENVIRONMENT_DIMENSION * 0.9) / 2; //ring radius
			double offset = (Options.ENVIRONMENT_DIMENSION / 2) - 0.01; //useful for centering everything
			int k = 0; //counter
			
			for (Object obj : context) {
				//Calculate coordinates for each participant position
				double x = radius * Math.cos((k * 2 * Math.PI) / n) + offset;
				double y = radius * Math.sin((k * 2 * Math.PI) / n) + offset;
				space.moveTo(obj, x, y);
				k++;
			}
		} else if(topology.compareTo("*") == 0) { //Extended star topology
			int layer = 0; //a star is composed by multiple layers of nodes
			int k = 0;
			double offset = (Options.ENVIRONMENT_DIMENSION / 2) - 0.01; //useful for centering everything
			
			
			int tempSum = 0, layerNum = -1; //find out the number of layers
			for(int i=1; i<n && layerNum == -1; i++) {
				tempSum += i;
				if((n - 1) - (4 * tempSum) <= 0)
					layerNum = i;
			}
			
			double interval = (Options.ENVIRONMENT_DIMENSION * 0.45) / layerNum;
			for (Object obj : context) {
				//125 is the maximum amount of participant that can be used during simulation

				double layerSize = 4 * layer;
				
				if(layer == 0) { //This is the (first) central peer
					double centerX = Options.ENVIRONMENT_DIMENSION / 2;
					double centerY = Options.ENVIRONMENT_DIMENSION / 2;
					space.moveTo(obj, centerX, centerY);
					layer++;
				} else { //all the other participants follow this rule
					double radius = interval * layer;
					
					double x = radius * Math.cos((k * 2 * Math.PI) / layerSize) + offset;
					double y = radius * Math.sin((k * 2 * Math.PI) / layerSize) + offset;
					space.moveTo(obj, x, y);
					k++;
					
					if(k == layerSize) {
						layer++;
						k = 0;
					}
				}
			}
		} else if(topology.compareTo("|") == 0) { // Line topology
			double interval = (Options.ENVIRONMENT_DIMENSION * 0.9) / (n-1); //distance adjacent participants
			double y = Options.ENVIRONMENT_DIMENSION / 2;
			double start = (Options.ENVIRONMENT_DIMENSION - ((n-1) * interval)) / 2; //position of first participant
			
			int k = 0;
			
			for (Object obj : context) {
				double x = start + (k * interval);
				space.moveTo(obj, x, y);
				k++;
			}			
		} else {
			return;
		}
	}
	
	// Method to calculate how many updates are needed to reach 100% completion
	@ScheduledMethod(start = 0, interval = 25)
	public static void calcMissingUpdateRatio() {
		
		globalFrontier = new HashMap<>();
		 
		 //calculating global log length
		 for(Participant p : currentParticipants) {
			 globalFrontier.put(p.getId(), p.getLocalLogLength());
		 }
		 
		 //calculating ratios
		 for(Participant p : currentParticipants) {
			 p.calcLocalMissingUpdateRatio(globalFrontier);
		 }
	}
	
	@ScheduledMethod(start = 1, interval = 1)
	public static void saveData() {
		
		double tick = RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		
		//Saving failed and successfull connections
//		for(Participant p : currentParticipants) {
//			if(p.getFailedConnections() > 0) {
//				overallFailedConnections += p.getFailedConnections();
//				p.setFailedConnections(0);
//				overallSucceededConnections += p.getSucceededConnections();
//				p.setSucceededConnections(0);
//			}
//		}
//		DataCollector.saveConnections(overallSucceededConnections, overallFailedConnections, tick);
		
		//Saving number of participants
//		DataCollector.saveNumberOfParticipants(currentPeerNum, tick);
		
		//Saving number of news
//		for(Participant p: currentParticipants) {
//			overallNumberOfNews += p.getNumberOfNews();
//			overallNumberOfEdges += p.getIsSending();
//			p.setNumberOfNews(0);
//		}
//		
//		overallSummationNews += overallNumberOfNews;
//		DataCollector.saveNumberOfNews(overallNumberOfNews, overallNumberOfEdges, overallSummationNews, tick);
//		overallNumberOfNews = 0;
//		overallNumberOfEdges = 0;
		
		//Saving free bandwidth
//		for(Participant p: currentParticipants) {
//			overallNumberOfNews += p.getNumberOfNews();
//			p.setNumberOfNews(0);
//		}
//		
//		double freeBandwidth = ((Options.BANDWIDTH * currentPeerNum) - (overallNumberOfNews * Options.EVENT_SIZE)) / currentPeerNum;
//		DataCollector.saveFreeBandwidth(freeBandwidth, tick);
//		overallNumberOfNews = 0;
		
		//Saving convergence
//		if(tick % 25 == 0 || tick > 998) {
//			for(Participant p: currentParticipants) {
//				overallPercentage += p.getPercentageValue();
//			}
//			overallPercentage = overallPercentage / currentPeerNum;
//			DataCollector.saveLogPercentage(overallPercentage, tick);
//			overallPercentage = 0;
//		}
		
		//Save latency
//		Options.PROBABILITY_OF_EVENT = 0;
//		Options.PROBABILITY_TO_FOLLOW = 0;
//		Options.PROBABILITY_TO_BLOCK = 0;
//		Options.JOIN_PROBABILITY = 0;
//		Options.CRASH_PROBABILITY = 0;
//		
//		Participant tempParticipant = null;
//		
//		for(Participant p: currentParticipants) {
//			if(p.getLabel().equals("0"))
//				tempParticipant = p;
//		}
//		
//		for(Participant p: currentParticipants) {
//			if(p.hasLatencyEvent(tempParticipant)) {
//				updatedNodes++;
//			}
//		}
//		
//		DataCollector.saveLatency(updatedNodes, tick);
//		updatedNodes = 0;
		
	}
	
	
	public static ContinuousSpace<Object> getSpace() {
		return space;
	}
	
	public static int getCurrentParticipantNum() {
		return currentPeerNum;
	}
	
	public static int getUniqueRelaysNum() {
		return nextId;
	}
	
}
