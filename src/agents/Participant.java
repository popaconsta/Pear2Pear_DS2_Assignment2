package agents;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.crypto.SealedObject;
import pear2Pear_DS2_Assignment2.TopologyManager;
import repast.simphony.context.Context;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.graph.Network;
import repast.simphony.util.ContextUtils;
import Utils.Options;
import communication.Event;
import communication.Handshake;
import communication.Interest;

/*
 * This class encapsulates the behavior of the participant.g
 */
public class Participant {
	
	public static final int AVAILABLE = 0;
	public static final int SYN_SENT = 1;
	public static final int SYN_RECEIVED = 2;
	public static final int ESTABLISHED = 3;
	public static final int NEWS_EXCHANGED = 4;
	public static final int FINISHED = 5;

	TopologyManager topologyManager; //object containing useful methods for adding and removing participants
	private View view; //partial view of the other neighbour participants
	private PublicKey id; //public key of the participant 
	private PrivateKey privateKey; //private key of the participant 
	private Map<PublicKey, CopyOnWriteArrayList<Event>> store; //append-only store containing one log per participant
	private Map<PublicKey, Integer> frontier; //reference of next expected event for each participant
	private int clock; //Incrementally growing id for the emitted events
	private int state;
	
	private List<Handshake> handshakeSYNs;
	private List<Handshake> handshakeACKs;
	private Participant currentPeer;
	private Map<PublicKey, Integer> peerFrontier;
	private Map<PublicKey, List<Event>> peerNews;
	private String label;

	//Relays generate perturbations with a given probability value
	private double probabilityOfNewEvent;
	private double probabilityOfHandshake = 0.15; //TODO:parametrize
	private int timeout;
	
	private enum InterestType {
		follow(1),
		unfollow(2),
		block(3),
		unblock(4);
		
		private int value;
		private InterestType(int value) {
			this.value = value;
		}
		
		public int getValue() {
			return this.value;
		}
	}
	
	private Map<PublicKey, List<PublicKey>> follows;
	private Map<PublicKey, List<PublicKey>> blocks;
	
	
	public Participant(PublicKey id, PrivateKey privateKey, String label) {
		super();
		this.id = id;
		this.privateKey = privateKey;
		this.label = label;
		
		view = new View(Options.MAX_PARTICIPANT_COUNT / 4); //each view contains a quarter of the participants
		clock = 0;
		timeout = 0;
		state = AVAILABLE;
		currentPeer = null;
		handshakeSYNs = new ArrayList<>();
		handshakeACKs = new ArrayList<>();
		store = new HashMap<>();
		frontier = new HashMap<>();
		probabilityOfNewEvent = Options.PROBABILITY_OF_PERTURBATION;
		follows = new HashMap<>();
		blocks = new HashMap<>();
	}




	/*
	 * At each tick, the relays generate a new perturbation if the
	 * random generated number is smaller than "probabilityOfEvent"
	 */
	@ScheduledMethod(start=1, interval=1, priority=99) 
	public void generateEvent() {
		double coinToss = RandomHelper.nextDoubleFromTo(0, 1);
		//Generate a broadcast with one fourth of the probability
		if(coinToss <= probabilityOfNewEvent) { //propagate a value broadcast perturbation
			
			appendToLog(new String("ciao"));
			
			//TODO: see if you are going to follow or block someone; add to follows and blocks
			
		} 
	}
	
	
	@ScheduledMethod(start=1, interval=1, priority=50) 
	public void runOpenGossipProtocol() {
		
		int tickCount = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		Context<Object> context = ContextUtils.getContext(this);
		Network<Object> net = (Network<Object>) context.getProjection("handshake network");
		
		if(state == AVAILABLE) {
			currentPeer = pickRandomSyn();
			if(currentPeer != null) {
				System.out.println("Participant(" + label + "): sending ACK for SYN sent by " + currentPeer.getLabel());
				currentPeer.onHandshakeACK(new Handshake(tickCount, Handshake.SYN_ACK, this));
				state = SYN_RECEIVED;
				//add an edge between this partecipant and its peer
				timeout = 2; //TODO: parametrize
			} else {
				double coinToss = RandomHelper.nextDoubleFromTo(0, 1);
				if(coinToss <= probabilityOfHandshake) {
					currentPeer = view.getRandom();
					System.out.println("Participant(" + label + "): sending SYN to " + currentPeer.getLabel());
					currentPeer.onHandshakeSyn(new Handshake(tickCount, Handshake.SYN, this));
					state = SYN_SENT;
					timeout = 2; //TODO: parametrize
					net.addEdge(this, currentPeer);
				}
			}	
		} else if(timeout < 0) {
			System.out.println("Participant(" + label + "): " + currentPeer.getLabel() + " didn't ACK in time, aborting...");
			net.removeEdge(net.getEdge(this, currentPeer));
			currentPeer = null;
			state = AVAILABLE;
			handshakeSYNs.clear();
			handshakeSYNs.clear();
		} else if(state == SYN_SENT || state == SYN_RECEIVED) {
			timeout--;
			if(establishConnection()) {
				state = ESTABLISHED;
				System.out.println("Participant(" + label + "): established connection to " + currentPeer.getLabel());
				Map<PublicKey, Integer> myFrontier = getFrontierByIds(getStoreIds());
				currentPeer.onEstablished(myFrontier);
				handshakeSYNs.clear();
				handshakeACKs.clear();
				timeout = 2; //reset timeout
			}
		} else if(state == ESTABLISHED && peerFrontier != null) {
			System.out.println("Participant(" + label + "): inspecting frontier from " + currentPeer.getLabel());
			
			for(Entry<PublicKey, Integer> entry : peerFrontier.entrySet()) {
				PublicKey tempId = entry.getKey();
				if(!store.containsKey(tempId)) {
					addLogToStore(tempId);
					frontier.put(tempId, -1);
				}
			}
			
			Map<PublicKey, List<Event>> news = getEventsSince(peerFrontier);
			currentPeer.onNewsExchange(news);
			
			System.out.println("Participant(" + label + "): exchanging news from "
					+ news.size() + "partecipant(s) with " + currentPeer.getLabel());
			
			state = NEWS_EXCHANGED;
		} else if(state == NEWS_EXCHANGED && peerNews != null) {
			System.out.println("Participant(" + label + "): closing connection to " 
					+ currentPeer.getLabel() + " and applying updates from " + peerNews.size() + " participants.");
			
			//Algorithm 3
			//compute new follows and blocks
			for(Entry<PublicKey, List<Event>> entry : peerNews.entrySet()) {
				updateInterests(entry.getKey(), entry.getValue());
			}
			
			// Line 3 of algorithm
			for(PublicKey followed : follows.get(currentPeer.getPublicKey())) {
				if(!getStoreIds().contains(followed))
					addLogToStore(followed);
			}
			
			//Line 4 of algorithm
			for(PublicKey blocked : blocks.get(currentPeer.getPublicKey())) {
				if(getStoreIds().contains(blocked))
					removeLogFromStore(blocked);
			}
			
			updateStore(peerNews);
			

			
			state = FINISHED;
		} else if(state == FINISHED) {
			peerFrontier = null;
			peerNews = null;
			
			System.out.println("Connection closed, current status: store = " + store.size() );
			for(PublicKey tempId : store.keySet()) {
				System.out.println(getLogById(tempId).size());
			}
			
			//remove link with current peer
			net.removeEdge(net.getEdge(this, currentPeer));
			state = AVAILABLE;
		}
	}
	
	public void onHandshakeSyn(Handshake hs) {
		//reject request if the participant is already involved in another handshake
		if(state == AVAILABLE) 
			handshakeSYNs.add(hs);
	}
	
	public void onHandshakeACK(Handshake hs) {
		//reject the ACK unless it is the expected ACK 
		if((state == SYN_SENT || state == SYN_RECEIVED) && hs.getPeer().equals(currentPeer)) {
			handshakeACKs.add(hs);
		}
	}
	
	private Participant pickRandomSyn() {
		int tickCount = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		// Filter eligible SYNs (those that were received in the past, current tick is excluded)
		List<Handshake> eligibleSYNs = new ArrayList<>();
		for(Handshake hs : handshakeSYNs) {
			if(hs.getSentAt() < tickCount)
				eligibleSYNs.add(hs);
		}
		
		
		if(eligibleSYNs.size() == 0)
			return null;
		
		// Choose one among the handshake SYNs
		int randomSynIndex = RandomHelper.nextIntFromTo(0, eligibleSYNs.size() - 1);
		Handshake syn = eligibleSYNs.get(randomSynIndex);
		// Remove all the others and leave only the chosen peer
		handshakeSYNs.clear();
		handshakeSYNs.add(syn);
		
		return syn.getPeer();
	}
	
	private boolean establishConnection() {
		if(handshakeACKs.size() == 0)
			return false;
		
		int tickCount = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		boolean established = false;
		
		Handshake ack = handshakeACKs.get(0);
		
		if(ack.getSentAt() < tickCount) {
			
			if(ack.getType() == Handshake.SYN_ACK && state == SYN_SENT) {
				currentPeer.onHandshakeACK(new Handshake(tickCount, Handshake.ACK, this));
				established = true;
			} else if(ack.getType() == Handshake.ACK && state == SYN_RECEIVED) {
				established = true;
			}
		}
		
		return established;
	}
	
	public void onEstablished(Map<PublicKey, Integer> frontier) {
		this.peerFrontier = frontier;
	}
	
	public void onNewsExchange(Map<PublicKey, List<Event>> news) {
		this.peerNews = news;
	}
	
	
	/*
	 * extend the log with a new event created locally (as owner) from content
	 */
	private List<Event> appendToLog(Object content) {
		List<Event> log = getLogById(this.id);
		Integer lastIndex = (frontier.get(this.id) == null) ? -1 : frontier.get(this.id);
		
		System.out.println("Participant(" + label + "): appending event #" + (lastIndex+1));
		
		Integer previous = (lastIndex == -1) ? 0 : log.get(lastIndex).hashCode();
		Event e = new Event(this.id, previous, lastIndex + 1, content, this.privateKey);
		log.add(e);
		frontier.put(id, lastIndex + 1);	
		
		return log;
	} 
	
	/*
	 * extend the log with the subset of compatible events previously created remotely
	 */
	private List<Event> updateLog(List<Event> news) {
		if(news.size() == 0)
			return null;
		
	    List<Event> log = getLogById(news.get(0).getId());
	    for(Event e : news) {
	    	Integer lastIndex = frontier.get(news.get(0).getId());
	    	lastIndex = lastIndex == null ? -1 : lastIndex;
	    	Integer previous = lastIndex == -1 ? 0 : log.get(lastIndex).hashCode();
	    	
	    	System.out.println("last " + lastIndex + "idx " 
	    	+ e.getIndex() + " prev " + e.getPrevious() + " prv " + previous + " " + e.isSignatureVerified());
	    	System.out.println((e.getIndex().equals(lastIndex + 1)) 
	    			+ "\n" + (previous.equals(e.getPrevious()))
	    			+ "\n" + (e.isSignatureVerified())
	    			+ "\n------------------------------------");
	    	
	    	if(e.getIndex().equals(lastIndex + 1)
	    			&& previous.equals(e.getPrevious())
	    			&& e.isSignatureVerified()) {
	    		System.out.println("Participant(" + label + "): applying update "
	    				+ "#" + e.getIndex());
				
	    		log.add(e);
	    		frontier.put(e.getId(), e.getIndex());
	    	}
	    }
	    
		return log;
	}
	
	/*
	 * get the set of events with index included between start and end (can be the same)
	 */
	private List<Event> getEventsBetween(PublicKey id, int start, int end) {
		List<Event> events = new ArrayList<>();
		List<Event> log = getLogById(id);
		
		for(int i=start; i<=end; i++) {
			events.add(log.get(i));
		}
		
		return events;
	}
	
	/*
	 * **************************************
	 * ********** STORE OPERATIONS **********
	 * **************************************
	 */
	
	/*
	 * add a log to the store 
	 */
	private List<Event> addLogToStore(PublicKey id) {	
		CopyOnWriteArrayList<Event> log = new CopyOnWriteArrayList<>();
		store.put(id, log);
		return log;
	}
	
	/*
	 * remove the log with id from the store 
	 */
	private void removeLogFromStore(PublicKey id) {
		store.remove(id);
	}
	
	/* 
	 * get the log with id from the store, if present 
	 */
	private List<Event> getLogById(PublicKey id) {
		List<Event> log = store.get(id);
		
		if(log == null)
			log = addLogToStore(id);
		
		return log;
	}
	 
	
	/*
	 * get the set of ids of the logs in the store 
	 */
	private Set<PublicKey> getStoreIds() {
		return store.keySet();
	}
	
	
	/*
	 * get the current frontier of the store only for ids
	 */
	private Map<PublicKey, Integer> getFrontierByIds(Set<PublicKey> ids) {
		Map<PublicKey, Integer> partialFrontier = new HashMap<>();
		
		for(Entry<PublicKey, Integer> entry : frontier.entrySet()) {
			PublicKey tempId = entry.getKey();
			if(ids.contains(tempId)) {
				partialFrontier.put(tempId, entry.getValue());
			}
		}
		
		return partialFrontier;
	}
	
	/*
	 * get the set of events that happened after frontier
	 */
	private Map<PublicKey, List<Event>> getEventsSince(Map<PublicKey, Integer> frontier) {
		
		Map<PublicKey, List<Event>> events = new HashMap<>(); 
		
		for(Entry<PublicKey, Integer> entry : frontier.entrySet()) {
			PublicKey tempId = entry.getKey();
			Integer tempSince = entry.getValue();
			if(tempId.equals(id))
				System.out.println(label + " temp since " + tempSince);
			List<Event> log = getLogById(tempId);
			List<Event> eventsById = new ArrayList<>();
			for(Event e : log) {
				if(e.getIndex() > tempSince) {
					eventsById.add(e);
				}
			}
			System.out.println("events by id size" + eventsById.size());
			events.put(tempId, eventsById);
		}
		
		return events;
	}
	
	/*
	 * update the logs in store with events
	 */
	private void updateStore(Map<PublicKey, List<Event>> news) {
		System.out.println("Participant(" + label + "): updating store with news for " 
				+ news.size() + " participant(s)");
		
		for(PublicKey tempId : news.keySet()) {
			System.out.println("news by id size" + news.get(tempId).size());
			List<Event> newsById = news.get(tempId); //new events
			//List<Event> log = get(tempId); //target log for append operations
			updateLog(newsById);
		}
	}
	
	/*
	 * ****************************************************
	 * ********** TRANSITIVE-INTEREST OPERATIONS **********
	 * ****************************************************
	 */
	
	/*
	 * publicly record in log active interest in id
	 */
	private void follow(PublicKey id) {
		appendToLog(new Interest(id, InterestType.follow.getValue()));
	}
	
	private void unfollow(PublicKey id) {
		appendToLog(new Interest(id, InterestType.unfollow.getValue()));
	}
	
	private void block(PublicKey id) {
		appendToLog(new Interest(id, InterestType.block.getValue()));
	}
	
	private void unblock(PublicKey id) {
		appendToLog(new Interest(id, InterestType.unblock.getValue()));
	}
	
	private void updateInterests(PublicKey id, List<Event> news) {
		for(Event e : news) {
			if(e.getContent() instanceof Interest) {
				if(((Interest)e.getContent()).getType() == InterestType.follow.getValue()) {
					addToFollows(id, ((Interest)e.getContent()).getTarget());
				}
				if(((Interest)e.getContent()).getType() == InterestType.unfollow.getValue()) {
					removeFromFollows(id, ((Interest)e.getContent()).getTarget());
				}
				if(((Interest)e.getContent()).getType() == InterestType.block.getValue()) {
					addToBlocks(id, ((Interest)e.getContent()).getTarget());
				}
				if(((Interest)e.getContent()).getType() == InterestType.unblock.getValue()) {
					removeFromBlocks(id, ((Interest)e.getContent()).getTarget());
				}
			}
		}
	}
	
	
	// Note up that id follows target; if target is blocked, unblock
	private void addToFollows(PublicKey id, PublicKey target) {
		
		if(blocks.containsKey(id) && blocks.get(id).contains(target)) {
			removeFromBlocks(id, target);
		}
		if(!follows.containsKey(id)) {
			follows.put(id, new ArrayList<>());
		}
				
		if(!follows.get(id).contains(target)) {
			follows.get(id).add(target);
		}
	}
	
	// Note up that id does not follow target anymore
	private void removeFromFollows(PublicKey id, PublicKey target) {
		if(follows.containsKey(id) && follows.get(id).contains(target)) {
			follows.get(id).remove(target);
		}
	}
	
	// Note up that id blocks target; if target is followed, unfollow
	private void addToBlocks(PublicKey id, PublicKey target) {
		
		if(follows.containsKey(id) && follows.get(id).contains(target)) {
			removeFromFollows(id, target);
		}
		if(!blocks.containsKey(id)) {
			blocks.put(id, new ArrayList<>());
		}
		
		if(!blocks.get(id).contains(target)) {
			blocks.get(id).add(target);
		}
	}
	
	//Note up that id odes not block target anymore
	private void removeFromBlocks(PublicKey id, PublicKey target) {
		if(blocks.containsKey(id) && blocks.get(id).contains(target)) {
			blocks.get(id).remove(target);
		}
	}
	
	public View getView() {
		return view;
	}
	
	public void setView(View view) {
		this.view = view;
	}
	
	public int getState() {
		return state;
	}
	
	public Participant getCurrentPeer() {
		return currentPeer;
	}
	
	public String getLabel() {
		return label;
	}
	
	public PublicKey getPublicKey() {
		return id;
	}
	
//	//Relay__II private Map<Integer, ArrayList<Perturbation>> bag; //Out-of-order perturbations go here, waiting to be delivered later
//		private Map<Integer, ArrayList<Perturbation>> log; //append-only log, one log per source
//		TopologyManager topologyManager; //object containing useful methods for adding and removing relays
//		public int id; //Globally unique id of the relay
//		private int clock = 0; //Incrementally growing id for the emitted perturbations
//		private Map<Integer, Integer> frontier; //reference of next perturbation per peer to be delivered
//		private HashSet<String> seenARQs; //This is needed to filter out already seen ARQs and save computation time
//		private Map<Integer, List<String>> subscriptions; //The groups and the topics this relay has subscribed to
//		private Hashtable<Perturbation, Integer> livePerturbations; //The set of perturbations this relay has forwarded AND are still alive
//		private boolean crashed;
//		
//		//Relays generate perturbations with a given probability value
//		private double probabilityOfPerturbation = Options.PROBABILITY_OF_PERTURBATION;
//		
//		public Relay(int id) {
//			this.log = new HashMap<>();
//			//Relay__II this.bag = new HashMap<>();
//			this.frontier = new HashMap<>();
//			this.seenARQs = new HashSet<>();
//			this.id = id;
//			livePerturbations = new Hashtable<>();
//			subscriptions = new HashMap<>();
//			crashed = false;
//			
//			/*
//			* Random way to generate subscriptions and test the overall multicast messagging
//			* Basically the nodes whose id is a multiple of 7 (0, 7, 14, 21...)
//			* will be subscribe to the group with id=0 and will listen for the topics science and literature
//			*/
//			if(id % 7 == 0) {
//				List<String> topics = new ArrayList<>();
//				topics.add("science");
//				topics.add("literature");
//				//this relay subscribes to group 0 for the topics science and literature
//				//subscriptions can have also no topics (empty topics list), in this case the node 
//				//will receive all the messages from the group, so no filtering will be made
//				subscriptions.put(0, topics);
//			}
//				
//			
//		}
//
//		/*
//		 * At each tick, the relays generate a new perturbation if the
//		 * random generated number is smaller than "probabilityOfPerturbation"
//		 * Then, based on the specific value it decides the type of perturbation.
//		 * The payload of the perturbations is always the same, but it is not relevant 
//		 * for our purposes, in terms of performance of the algorithm, since we assume
//		 * that all the perturbations have the same size.
//		 */
//		@ScheduledMethod(start=1, interval=1, priority=50) 
//		public void generatePerturbation() {
//			double coinToss = RandomHelper.nextDoubleFromTo(0, 1);
//			//Generate a broadcast with one fourth of the probability
//			if(coinToss <= (probabilityOfPerturbation / 4)) { //propagate a value broadcast perturbation
//				broadcast(new String("ciao"));
//				
//			//Generate a (unencrypted) unicast message if the value is between 1/4 and 2/4 of p
//			} else if((coinToss > (probabilityOfPerturbation / 4)) 
//					&& (coinToss <= ((probabilityOfPerturbation / 4) * 2))) { //private message
//				//each relays sends a private message to relay with identifier equal to id+1
//				int destination = (id + 1) % TopologyManager.getUniqueRelaysNum();
//				send(destination, new String("ciao"));
//				
//			//Finally generate a group message if the probability is between 2/4 of p and 3/4 of p
//			} else if((coinToss > ((probabilityOfPerturbation / 4) * 2)) 
//					&& (coinToss <= ((probabilityOfPerturbation / 4) * 3))) { //private message
//				//each relays sends a private message to relay with identifier equal to id+1
//				int secretDestination = (id + 1) % Options.MAX_RELAY_COUNT;
//				privateSend(secretDestination, new String("ciao"));
//				
//			//Finally generate a group message if the probability is between 3/4 of p and p
//			} else if((coinToss > ((probabilityOfPerturbation / 4) * 3)) 
//					&& (coinToss <= probabilityOfPerturbation)) {
//				groupSend(0, "science", "1+1=2");
//			}
//		}
//		
//		
//		//Method used to forward a perturbation 
//		private void forward(Perturbation p) {
//			System.out.println("Relay(" + id + "): forwarding perturbation"
//					+ "<" + p.getSource() + ", " + p.getReference() + ", " + p.getPayload().toString() + ">");
//			
//			NdPoint spacePt = TopologyManager.getSpace().getLocation(this);
//			Context<Object> context = ContextUtils.getContext(this);
//			
//			if(livePerturbations.get(p) != null)
//				livePerturbations.put(p, livePerturbations.get(p) + DiscretePropagation.PROPAGATION_ANGLES.length);
//			else
//				livePerturbations.put(p, DiscretePropagation.PROPAGATION_ANGLES.length);
//			
//			/*
//			* The propagation of a perturbation/wave is simulated by generating 8 perturbation clones
//			* and propagating them along the 9 directions/angles (0, 45, 90, 135...)
//			* Each propagation clone travels at a speed determined by the available bandwidth 
//			* of the relay which forwarded it, so it can reach the maximum range faster than others propagations
//			* The clone is called Discrete Propagation and it's a "piece" of a Perturbation
//			*/
//			for(int i=0; i<DiscretePropagation.PROPAGATION_ANGLES.length; i++) {
//				DiscretePropagation propagation = new DiscretePropagation(
//						p, DiscretePropagation.PROPAGATION_ANGLES[i], this);
//				context.add(propagation);
//				//Finally place the perturbation in the space
//				//Initially the perturbation has the same position as the source, 
//				//then it moves (propagates) at each interval step
//				TopologyManager.getSpace().moveTo(propagation, spacePt.getX(), spacePt.getY());
//			}
//		}
//		
//		
//		/*
//		 * This method represents the Automatic Retransmission Mechanism described
//		 * in the paper (ARQ). In order to prevent massive flooding which such requests
//		 * relays use a smart mechanism which prevents the simulation from blocking.(details below)
//		 */
//		@ScheduledMethod(start=1, interval=1, priority=20)
//		public void automaticRetransmissionMechanism() {
//			//Get the current tick number
//			int tickCount = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
//			/*
//			* Relay activate this mechanism at different times. 
//			* This is needed in order to reduce the flooding effect 
//			* when all the relays start propagating ARQs at the same time.
//			* e.g: Relay 1, 11, 21, 31....91, 101, 111, 121 etc send ARQ at tick 1, 11, 21, 31.....
//			* Hence, relays whose id end with 1, send ARQ during ticks which also end with number 1
//			*/
//			if(tickCount % 10 == id % 10) {
//				//For each known source, send a request for the next expected perturbation
//				for(Map.Entry<Integer, ArrayList<Perturbation>> perSourceLog : log.entrySet()) {
//					//Avoid sending ARQs for your own perturbations
//					if(perSourceLog.getKey() != this.id) {
//						List <Perturbation> perturbations = perSourceLog.getValue();
//						//Find out which was the last perturbation for that source
//						Perturbation latestPerturbation = perturbations.get(perturbations.size() - 1);
//						System.out.println("Relay(" + id + "): broadcasting ARQ for perturbation " 
//								+ "<src=" + latestPerturbation.getSource() + ", "
//								+ "ref=" + (latestPerturbation.getReference()+1) + ">");
//						//This is needed in order to identify ARQs, since src and ref are not enough.
//						//Multiple relays might send ARQs for the same perturbation, and in this can 
//						//we need a way to distinguish them. UUIDs is the solution to this problem
//						final String uuid = UUID.randomUUID().toString();
//						seenARQs.add(uuid);
//						forward(new Perturbation(latestPerturbation.getSource(), 
//								latestPerturbation.getReference() + 1, Type.RETRANSMISSION_REQUEST, uuid)); 
//					}
//				}
//			}
//		}
//
//		/*
//		 * At each tick, relays "look" around them to see if there are any 
//		 * new perturbations in their local domain broadcast
//		 */
//		@ScheduledMethod(start=1, interval=1, priority=80)
//		public void sense() {
//			Context<Object> context = ContextUtils.getContext(this);
//			List<Perturbation> perturbations = new ArrayList<Perturbation>();
//			
//			//Build a query which returns all the perturbations in this relay's range
//			ContinuousWithin<Object> nearbyQuery = new ContinuousWithin(context, this, Options.RELAY_RANGE);
//			//thread safe method to inspect the nearby propagations
//			CopyOnWriteArrayList<Object> nearbyObjects = new CopyOnWriteArrayList<>();
//			nearbyQuery.query().forEach(nearbyObjects::add);
//
//			//Iterate through the found perturbations
//			for(Object obj : nearbyObjects) {
//				if(obj instanceof DiscretePropagation && ((DiscretePropagation) obj).propagated) {
//					DiscretePropagation propagation = (DiscretePropagation) obj;
//					Perturbation p = propagation.getPerturbation();
//					Relay forwarder = propagation.getForwarder();
//					
//					if(p.getType() == Type.RETRANSMISSION_REQUEST) {
//						int src = p.getSource();
//						int ref = p.getReference();
//						if(!seenARQs.contains((String)p.getPayload())) {
//							System.out.println("Relay(" + id + "): sensed retransimission request for P="
//									+ "<src=" + src + ", ref=" + ref +">");
//							//add the ARQ to the set so it won't be processed twice
//							seenARQs.add((String)p.getPayload());
//							//If the requested perturbation is in the log, forward it
//							if(log.get(src) != null) {
//								for(Perturbation Q : log.get(src)) 
//									if(Q.getReference() == ref)
//										forward(Q);
//							} else {
//								forward(p);
//							}
//						}
//
//					//Check if the perturbation is the next expected perturbation
//					} else if((frontier.get(p.getSource()) == null
//							|| p.getReference() >= frontier.get(p.getSource())) 
//							&& p.getSource() != id && p.getType() != Type.RETRANSMISSION_REQUEST) { 
//							//Relay__II&& !isInBag(p)) {//don't sense self-generated perturbations
//						
//						//Relay__II addToBag(p);
//						
//						System.out.println("Relay(" + id + "): sensed perturbation"
//								+ "<" + p.getSource() + ", " + p.getReference() + ", " + p.getPayload().toString() 
//								+ "> fowarded by " + forwarder.getId());
//						
//						//calculate the next expected perturbation reference
//						Integer nextRef = frontier.get(p.getSource());
//						//in case it is null, it means this is the first perturbation for this source
//						if(nextRef == null) 
//							nextRef = p.getReference(); 
//						if(nextRef == p.getReference()) {
//							forward(p);
//							deliver(p);
//							frontier.put(p.getSource(), nextRef + 1);//update frontier
//
//							//add an edge between the relay who forwarded the perturbation and the receiver
//							Network<Object> net = (Network<Object>) context.getProjection("delivery network"); //TODO: fix churn
//							if(!forwarder.isCrashed())
//								net.addEdge(this, forwarder);
//						}
//							
//						//Relay__II(the entire loop)
//						//go through the bag until you do not make any new change
////								boolean changes = true;
////								while(changes) {
////									changes = false;
//							//Relay__II Iterator<Perturbation> deferredPerturbations = bag.get(p.getSource()).iterator();
////									while (deferredPerturbations.hasNext()) {
////									    Perturbation Q = deferredPerturbations.next();
////									    Integer nextRef = frontier.get(Q.getSource());
////										if(nextRef == null) 
////											nextRef = Q.getReference(); 
////										if(nextRef == Q.getReference())
////											changes = true;
////											forward(Q);
////											deliver(Q);
////											frontier.put(Q.getSource(), nextRef + 1);
////											//removeFromBag(Q);
////											deferredPerturbations.remove(); //temporary workaround
////									}
////								}
//					}
//					
//				}
//			}
//		}
//		
//		/*
//		 * At each tick, the relay might crash with a given probability.
//		 * The priority of this method is random, therefore it might occur
//		 * at different times with respect to the other scheduled methods.
//		 */
//		@ScheduledMethod(start=1, interval=1) //random priority by default
//		public void crash() {
//			double coinToss = RandomHelper.nextDoubleFromTo(0, 1);
//			if(coinToss <= Options.CRASH_PROBABILITY 
//					&& id != Options.NODE_A_BROADCAST //these nodes are needed for the benchmark
//					&& id != Options.NODE_B_BROADCAST) { //so make them immune to crashes
//				
//				System.out.println("Relay(" + id + ") crashed, removing it from the context...");
//				crashed = true;
//				TopologyManager.removeRelay(this);
//			}
//		}
//			
//		
//		//check if the perturbation is present in the bag
//		//Relay__II
////		private boolean isInBag(Perturbation p) {
////			boolean result = false;
////			if(bag.containsKey(p.getSource())) {
////				result = bag.get(p.getSource()).contains(p);
////			}
////			return result;
////		}
//		
//		//add a new perturbation to bag
//		//Relay__II
////		private void addToBag(Perturbation p) {
////			if(!bag.containsKey(p.getSource())) {
////				bag.put(p.getSource(), new ArrayList<Perturbation>());
////			}
////			bag.get(p.getSource()).add(p.clone());
////		}
//		
//		//remove perturbation from bag
//		//Relay__II
////		private void removeFromBag(Perturbation p) {
////			bag.get(p.getSource()).remove(p);
////		}
//		
//		//Deliver a perturbation
//		private void deliver(Perturbation p) {
//			//Filter out the perturbations generated by this relay
//			if(p.getSource() != this.id) {
//				System.out.println("Relay(" + id + "): delivering perturbation"
//						+ "<" + p.getSource() + ", " + p.getReference() + ", " + p.getPayload().toString() + ">");
//				
//				//Inspect the payload
//				if(p.getType() == Type.UNICAST_MESSAGE) {
//					UnicastMessage m = (UnicastMessage)p.getPayload();
//					if(m.getDestination() == this.id) {
//						System.out.println("Relay(" + id + "): Relay(" + p.getSource() + ") sent me a private message");
//					}
//				} else if(p.getType() == Type.VALUE_BROADCAST) {
//					//nothing
//				} else if(p.getType() == Type.MULTICAST_MESSAGE) {
//					MulticastMessage m = (MulticastMessage)p.getPayload();
//					//Check if I'm subscribed to the group and/or topic
//					if(subscriptions.get(m.getGroup()) != null) {
//						List<String> topics = subscriptions.get(m.getGroup());
//						if(topics == null || topics.contains(m.getTopic())) {
//							
//							System.out.println("Relay(" + id + "): received a new message "
//									+ "for the subscription in the group " + m.getGroup());
//						}
//					}
//				} else if(p.getType() == Type.ENCRYPTED_UNICAST) {
//					//Attempt to decrypt the message
//					SealedObject encryptedMessage = (SealedObject)p.getPayload();
//					UnicastMessage decryptedMessage = AsymmetricCryptography.decryptPayload(
//							encryptedMessage, KeyManager.PRIVATE_KEYS.get(this.id));
//					
//					//If the result is null, it means the private key is not the correct one,
//					//therefore the perturbation is addressed to a different relay.
//					if(decryptedMessage != null) {
//						System.out.println("Relay(" + id + "): Relay(" + p.getSource() + ") sent me an encrypted private message");
//					} else {
//						System.out.println("Relay(" + id + "): Encrypted message from Relay " +
//								p.getSource() + " couldn't be decrypted.");
//					}
//				}
//			}
//			
//			
//			//If not present, add the new source in log
//			if(!log.containsKey(p.getSource()))
//				log.put(p.getSource(), new ArrayList<Perturbation>());
//			
//			//Add the perturbation to the associated source's list
//			log.get(p.getSource()).add(p);
//			
//			//If this node is in charge of collecting data about latency and the sender of the perturbation is too, collect data
//			if(this.id == Options.NODE_B_BROADCAST && p.getSource() == Options.NODE_A_BROADCAST) {
//				DataCollector.saveLatency(p, RunEnvironment.getInstance().getCurrentSchedule().getTickCount(), "LatencyReceiver.csv");
//			}
//			
//		}
//		
//		//Perturbation broadcast
//		private void broadcast(Object value) {
//			System.out.println("Relay(" + id + "): generating perturbation"
//					+ "<" + id + ", " + this.clock + ", val>");
//			
//			//Generate perturbation and deliver to yourself
//			Perturbation perturbation = new Perturbation(this.id, this.clock++, Type.VALUE_BROADCAST, value);
//			forward(perturbation);
//			deliver(perturbation);
//			
//			//Write generated message for latency measurement
//			if(this.id == Options.NODE_A_BROADCAST)
//				DataCollector.saveLatency(perturbation, RunEnvironment.getInstance().getCurrentSchedule().getTickCount(), "LatencySender.csv");
//		}
//		
//		//Helper method for generating a (unencrypted) private message which can then be forwarded using broadcast primitives
//		private void send(int destination, Object value) {
//			System.out.println("Relay(" + id + "): generating perturbation"
//					+ "<" + id + ", " + this.clock + ", U.M.>");
//			
//			//Create the payload and encrypt it
//			UnicastMessage m = new UnicastMessage(destination, value);
//			
//			//Generate perturbation 
//			Perturbation perturbation = new Perturbation(this.id, this.clock++, Type.UNICAST_MESSAGE, m);
//			forward(perturbation);
//			deliver(perturbation);
//			
//			//Write generated message for latency measurement
//			if(this.id == Options.NODE_A_BROADCAST)
//				DataCollector.saveLatency(perturbation, RunEnvironment.getInstance().getCurrentSchedule().getTickCount(), "LatencySender.csv");
//		}
//		
//		//Helper method for generating a private message which can then be forwarded using broadcast primitives
//		private void privateSend(int destination, Object value) {
//			System.out.println("Relay(" + id + "): generating perturbation"
//					+ "<" + id + ", " + this.clock + ", P.M.>");
//			
//			//Create the payload and encrypt it
//			UnicastMessage m = new UnicastMessage(destination, value);
//			SealedObject secret = AsymmetricCryptography.encryptPayload(m, KeyManager.PUBLIC_KEYS.get(destination));
//			
//			//Generate perturbation 
//			Perturbation perturbation = new Perturbation(this.id, this.clock++, Type.ENCRYPTED_UNICAST, secret);
//			forward(perturbation);
//			deliver(perturbation);
//			
//			//Write generated message for latency measurement
//			if(this.id == Options.NODE_A_BROADCAST)
//				DataCollector.saveLatency(perturbation, RunEnvironment.getInstance().getCurrentSchedule().getTickCount(), "LatencySender.csv");
//		}
//		
//		//Helper method for generating a group message which can then be forwarded using broadcast primitives
//		private void groupSend(int groupId, String topic, Object value) {
//
//					System.out.println("Relay(" + id + "): generating perturbation"
//							+ "<" + id + ", " + this.clock + ", G.M.>");
//					
//			MulticastMessage m = new MulticastMessage(groupId, topic, value);
//			Perturbation perturbation = new Perturbation(this.id, this.clock++, Type.MULTICAST_MESSAGE, m);
//			forward(perturbation);
//			deliver(perturbation);
//			
//			//Write generated message for latency measurement
//			if(this.id == Options.NODE_A_BROADCAST)
//				DataCollector.saveLatency(perturbation, RunEnvironment.getInstance().getCurrentSchedule().getTickCount(), "LatencySender.csv");
//		
//		}
//		
//		@Override
//		public String toString() {
//			String result = "";
//			result += "Relay: " + this.id + " --- ";
//			for(Map.Entry<Integer, ArrayList<Perturbation>> logLine : log.entrySet()) {
//				result += " Source(" + logLine.getKey() + ") -> ";
//				for(Perturbation p : logLine.getValue()) {
//					result += " | <" + p.getSource() + ", " + p.getReference() + ", " + p.getPayload() +"> ";
//				}
//			}
//			return result;
//		}
//		
//		public String saveIsolation() {
//			
//			int numberOfEdges = 0;
//			
//			Context<Object> context = ContextUtils.getContext(this);
//			Network<Object> net = (Network<Object>) context.getProjection("delivery network");
//			numberOfEdges += net.getOutDegree(this);
//			numberOfEdges += net.getInDegree(this);
//			
//			if(numberOfEdges == 0)
//				DataCollector.saveIsolation(RunEnvironment.getInstance().getCurrentSchedule().getTickCount(), "IsolatedRelays.csv");
//			
//			return "";
//		}
//		
//		//When a perturbation reaches its maximum range, it release the used bandwidth
//		public void releaseBandwidth(Perturbation p) {
//			
//			//System.out.println(id + " atempts to release bandwidth for " + p.toString());
//			
//			if((livePerturbations.get(p) - 1) == 0) 
//				livePerturbations.remove(p);
//				//System.out.println(id + " removed " + p.toString());
//			
//			else 
//				livePerturbations.put(p, livePerturbations.get(p)-1);
//				//System.out.println(id + " updated " + p.toString() + " to " + livePerturbations.get(p));
//		}
//		
//		
//		//Method to calculate the effective bandwidth based on how many perturbations
//		//this relay has forwarded. The greater the number of perturbations, the slower
//		//the propagation speed among the perturbations 
//		public double getFairBandwidth() {
//			double totalSize = livePerturbations.size() * Options.PERTURBATION_SIZE;
//			double fairbandwidth = Options.BANDWIDTH / totalSize;
//			//Perturbations can't propagate faster than a given value, so there is an upper bound
//			if(fairbandwidth > Options.MAX_PROPAGATION_SPEED)
//				return Options.MAX_PROPAGATION_SPEED;
//			else
//				return fairbandwidth;
//		}
//		
//		public int getId() {
//			return id;
//		}
//		
//		public boolean isCrashed() {
//			return crashed;
//		}
	
}

