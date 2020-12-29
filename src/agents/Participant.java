package agents;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.text.DecimalFormat;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
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
import repast.simphony.engine.schedule.DefaultSchedulableActionFactory;
import repast.simphony.engine.schedule.DefaultScheduleFactory;
import repast.simphony.engine.schedule.ISchedulableAction;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.PriorityType;
import repast.simphony.engine.schedule.Schedule;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.graph.Network;
import repast.simphony.util.ContextUtils;
import Utils.Options;
import communication.Event;
import communication.Handshake;
import communication.Interest;

/*
 * This class encapsulates the behavior of the participant
 */
public class Participant {
	
	/*
	 * States for representing participant lifecycle (check report for a more in depth explanation)
	 */
	public enum State {
	    AVAILABLE, SYN_SENT, SYN_RECEIVED, CONN_ESTABLISHED, NEWS_EXCHANGED, FINISHED; 
	}

	String protocolVariant; //open gossip or transitive interest
	TopologyManager topologyManager; //object containing useful methods for adding and removing participants
	private View view; //partial view of the other neighbor participants
	private PublicKey id; //public key of the participant 
	private String label; //readable label which can be displayed during simulation
	private PrivateKey privateKey; //private key of the participant 
	private Map<PublicKey, CopyOnWriteArrayList<Event>> store; //append-only store containing one log per participant
	private Map<PublicKey, Integer> frontier; //reference of next expected event for each participant
	private State state; //state of this participant
	private int timeout; //timeout for the next reply
	boolean crashed; 
	
	private List<Handshake> handshakeSYNs; //queue of SYN messages received from others
	private List<Handshake> handshakeACKs; //queue of ACKs received from other peers
	private Participant currentPeer; // the peer I am communicating currently with
	private Map<PublicKey, Integer> peerFrontier; //the frontier of my communication partner
	private Map<PublicKey, List<Event>> peerNews; //the news sent by my communication partner
	
	private Map<PublicKey, List<PublicKey>> follows; //list of follows, per participant
	private Map<PublicKey, List<PublicKey>> blocks; //list of blocks, per participant
	
	//Data used for evaluation and graphs
	private double logPercentage; 
	private int failedConnections; 
	private int succeededConnections;
	private int numberOfNews;
	private int isSending;
	
	public Participant(PublicKey id, PrivateKey privateKey, String label) {
		super();
		this.id = id;
		this.privateKey = privateKey;
		this.label = label;
		
		timeout = 0;
		state = State.AVAILABLE;
		crashed = false;
		currentPeer = null;
		handshakeSYNs = new ArrayList<>();
		handshakeACKs = new ArrayList<>();
		store = new HashMap<>();
		frontier = new HashMap<>();
		protocolVariant = Options.PROTOCOL_VARIANT;
		follows = new HashMap<>();
		blocks = new HashMap<>();
		logPercentage = 0.0;
		failedConnections = 0;
		numberOfNews = 0;
		isSending = 0;
	}


	/*
	 * At each tick, the participants generate a new event if the
	 * random generated number is smaller than "probabilityOfEvent"
	 */
	@ScheduledMethod(start=1, interval=1, priority=99) 
	public void generateEvent() {
		int tickCount = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		
		//FOR DATA COLLECTION
//		if(tickCount == 1800) {
//			Options.PROBABILITY_OF_EVENT = 0;
//			Options.PROBABILITY_TO_FOLLOW = 0;
//			Options.PROBABILITY_TO_BLOCK = 0;
//			Options.JOIN_PROBABILITY = 0;
//			Options.CRASH_PROBABILITY = 0;
//		}
		
		//FOR DATA COLLECTION
//		if(label.equals("0") && tickCount < 2) {
//			appendToLog(new String("LATENCY"));
//		}
		
		double coinToss = RandomHelper.nextDoubleFromTo(0, 1);
		//Generate a broadcast with one fourth of the probability
		if(coinToss <= Options.PROBABILITY_OF_EVENT) { //propagate a value broadcast perturbation	
			appendToLog(new String("ciao"));
		} 
		else if (coinToss <= Options.PROBABILITY_OF_EVENT + Options.PROBABILITY_TO_FOLLOW && Options.PROTOCOL_VARIANT.equals("TRANSITIVE_INTEREST_GOSSIP")) {
			
			//follow one random participant followed by someone i already follow; if cannot find, pick a random one
			PublicKey temp = pickRandomFollowing();
			Participant target = null;
			if(temp != null) {
				target = pickRandomTransitivelyFollowed(temp);
				if(target == null) {
					target = view.getRandomPeer();
				}
			} else {
				target = view.getRandomPeer();
			}
			
			if(target != null)
				follow(target.getId());
			
		} else if (coinToss <= Options.PROBABILITY_OF_EVENT + Options.PROBABILITY_TO_FOLLOW + Options.PROBABILITY_TO_BLOCK && Options.PROTOCOL_VARIANT.equals("TRANSITIVE_INTEREST_GOSSIP")) {
			
			//block one random participant blocked by someone I follow; if cannot find, pick a random one
			PublicKey friendId = pickRandomFollowing();
			Participant target = null;
			if(friendId != null) {
				target = pickRandomTransitivelyBlocked(friendId);
				if(target == null) {
					target = view.getRandomPeer();
				}
			} else {
				target = view.getRandomPeer();
			}
			
			if(target != null )
				if(currentPeer != null && target.equals(currentPeer))
					return;
				else
					block(target.getId());
			
		}
		
	}
	
	/*
	 * This method implements the OPEN GOSSIP PROTOCOL
	 */
	@ScheduledMethod(start=1, interval=1, priority=49) 
	public void runOpenGossipProtocol() {
		if(!protocolVariant.equals("OPEN_GOSSIP")) 
			return;
		
		//if participant is in the handshake phase, perform the procedure
		if(state == State.AVAILABLE 
				|| timeout < 0 
				|| state == State.SYN_SENT 
				|| state == State.SYN_RECEIVED
				|| state == State.CONN_ESTABLISHED
				|| state == State.FINISHED) {
			
			peerHandshake();
			
		} else if(state == State.NEWS_EXCHANGED && peerNews != null) {
			//otherwise if participant has received news from a peer, apply them
			System.out.println("Participant(" + label + "): applying updates from " + peerNews.size() + " participants.");
			updateStore(peerNews);
			state = State.FINISHED;
		} 
	}
	
	/*
	 * This method implements the TRANSITIVE INTEREST GOSSIP PROTOCOL
	 */
	@ScheduledMethod(start=1, interval=1, priority=50) 
	public void runTransitiveInterestGossipProtocol() {
		if(!protocolVariant.equals("TRANSITIVE_INTEREST_GOSSIP")) 
			return;
		
		//if participant is in the handshake phase, perform the procedure
		if(state == State.AVAILABLE 
				|| timeout < 0 
				|| state == State.SYN_SENT 
				|| state == State.SYN_RECEIVED		
				|| state == State.CONN_ESTABLISHED
				|| state == State.FINISHED) {
			
			peerHandshake();
			
		} else if(state == State.NEWS_EXCHANGED) {
			//otherwise if participant has received news from a peer, apply them
			timeout--;
			
			if(peerNews != null) {
				System.out.println("Participant(" + label + "): applying updates from " + peerNews.size() + " participants.");
				
				//Algorithm 3
				//compute new follows and blocks
				for(Entry<PublicKey, List<Event>> entry : peerNews.entrySet()) 
					updateInterests(entry.getKey(), entry.getValue());
				
				
				// Line 3 of algorithm
				if(follows.containsKey(currentPeer.getId()))
					for(PublicKey followed : follows.get(currentPeer.getId())) 
						if(!getStoreIds().contains(followed) && (!blocks.containsKey(this.id) || !blocks.get(this.id).contains(followed))) {
							addLogToStore(followed);
							frontier.put(followed, -1);
							//follow(followed);
						}
					
				
				//Line 4 of algorithm
				if(blocks.containsKey(currentPeer.getId()))
					for(PublicKey blocked : blocks.get(currentPeer.getId())) 
						if(getStoreIds().contains(blocked) && follows.containsKey(this.id) && !follows.get(this.id).contains(blocked))
							removeLogFromStore(blocked);
				
				//Filter out blocked participants fron news
				Iterator<Entry<PublicKey, List<Event>>> itr = peerNews.entrySet().iterator();
				while(itr.hasNext()) {
					PublicKey pKey = itr.next().getKey();
					if(blocks.containsKey(this.id) && blocks.get(this.id).contains(pKey)) {
						itr.remove();
					} 
				}
				updateStore(peerNews);
				state = State.FINISHED;
			}
		} 
	}
	
	/*
	 * This method represents the handshake procedure with is common 
	 * to both open gossip and transitive interest protocol
	 */
	private void peerHandshake() {
		int tickCount = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		
		if(state == State.AVAILABLE) {
			//If participant is available, pick a random SYN (if there are any)
			currentPeer = pickRandomSyn();
			if(currentPeer != null) {
				System.out.println("Participant(" + label + "): sending ACK for SYN initiated by " + currentPeer.getLabel());
				//send back syn ack and update state
				currentPeer.onHandshakeACK(new Handshake(tickCount, Handshake.SYN_ACK, this));
				state = State.SYN_RECEIVED;
				view.add(TopologyManager.getParticipantById(currentPeer.getId()), tickCount);
				//add an edge between this partecipant and its peer
				timeout = Options.ACK_TIMEOUT; //2 ticks to receive back frontier 
			} else {
				double coinToss = RandomHelper.nextDoubleFromTo(0, 1);
				if(coinToss <= Options.PROBABILTY_OF_HANDSHAKE) {
					
					//choose a partner for initiating handshake
					do{
						currentPeer = view.getRandomPeer(); //pick a (non blocked) peer
					}while(currentPeer != null 
							&& blocks.containsKey(this.id) 
							&& blocks.get(this.id).contains(currentPeer.getId()));
					
					if(currentPeer != null) {
						System.out.println("Participant(" + label + "): sending SYN to " + currentPeer.getLabel());
						//Send SYN and update state and timeout
						currentPeer.onHandshakeSyn(new Handshake(tickCount, Handshake.SYN, this));
						state = State.SYN_SENT;
						timeout = Options.ACK_TIMEOUT; //2 ticks to receive back SYN ACK
						TopologyManager.addEdge(this, currentPeer);
					}
				}
			}	
		} else if(timeout < 0) {
			/*
			 * If the other peer hasn't replied in time, abort communication and set state
			 * to availble again. Finally, clear the communication queues.
			 */
			System.out.println("Participant(" + label + "): " + currentPeer.getLabel() + " didn't reply in time, aborting...");
			TopologyManager.removeEdge(this, currentPeer);
			currentPeer = null;
			state = State.AVAILABLE;
			handshakeSYNs.clear();
			handshakeSYNs.clear();
			failedConnections = failedConnections + 1; //evaluation
		} else if(state == State.SYN_SENT || state == State.SYN_RECEIVED) {
			timeout--;
			//If ACKs were received, try to establish connections
			if(establishConnection()) {
				state = State.CONN_ESTABLISHED;
				//update view with handshake partners
				view.updateLastSeen(currentPeer.getId(), tickCount);
				System.out.println("Participant(" + label + "): established connection to " + currentPeer.getLabel());
				//prepare frontier for partner peer
				Map<PublicKey, Integer> myFrontier = getFrontierByIds(getStoreIds());
				currentPeer.onEstablished(myFrontier);
				handshakeSYNs.clear();
				handshakeACKs.clear();
				//set timeout for receiving back news
				timeout = Options.FRONTIER_TIMEOUT; 
				succeededConnections = succeededConnections + 1;
			}
		} else if(state == State.CONN_ESTABLISHED) {
			timeout--;
			//if connection is established, send back news
			if(peerFrontier != null) {
				System.out.println("Participant(" + label + "): inspecting frontier from " + currentPeer.getLabel());
				for(Entry<PublicKey, Integer> entry : peerFrontier.entrySet()) {
					PublicKey tempId = entry.getKey();
					if(!store.containsKey(tempId)) {
						addLogToStore(tempId);
						frontier.put(tempId, -1);
					}
				}
				//inspect partner frontier and prepare news accordingly
				Map<PublicKey, List<Event>> news = getEventsSince(peerFrontier);
				
				int eventNum = 0;
				for(Entry<PublicKey, List<Event>> newsById : news.entrySet()) 
					eventNum += newsById.getValue().size();
				
				numberOfNews = eventNum;
				
				if(eventNum > 0)
					isSending = 1;
				
				//calculate upload time based on bandwidth
				int uploadTime = (int) Math.ceil((eventNum * Options.EVENT_SIZE) / Options.BANDWIDTH);		
				
//				new Schedule()
//				.schedule(
//						ScheduleParameters.createOneTime(tickCount + uploadTime, PriorityType.RANDOM),
//						currentPeer, 
//						"onNewsExchange", 
//						news)
//				.execute();
			
				
				RunEnvironment.getInstance().getCurrentSchedule().schedule(
						ScheduleParameters.createOneTime(tickCount + uploadTime, PriorityType.RANDOM),
						currentPeer, 
						"onNewsExchange", 
						news);
				
				//currentPeer.onNewsExchange(news);
				
				System.out.println("Participant(" + label + "): exchanging news from "
						+ news.size() + "partecipant(s) with " + currentPeer.getLabel() 
						+ "(" + uploadTime + " ticks remaining, " + eventNum 
						+ " events, started at " + tickCount + " )" );
			
				// update state and timeout
				state = State.NEWS_EXCHANGED;
				timeout = Options.NEWS_TIMEOUT;
			}
			
		} else if(state == State.FINISHED) {
			/*
			 * when the exchange is done, set state to available again
			 */
			peerFrontier = null;
			peerNews = null;
			isSending = 0;
			System.out.println("Connection closed to " + currentPeer.getLabel() 
				+ ", current status: store = " + store.size() );
			
//			for(PublicKey tempId : store.keySet()) {
//				System.out.println(getLogById(tempId).size());
//			}
			//remove link with current peer
			TopologyManager.removeEdge(this, currentPeer);
			currentPeer = null;
			state = State.AVAILABLE;
		}
	}
	
	public void onHandshakeSyn(Handshake hs) {
		//reject request if the participant is already involved in another handshake
		if(state == State.AVAILABLE) 
			handshakeSYNs.add(hs);
	}
	
	public void onHandshakeACK(Handshake hs) {
		//reject the ACK unless it is the expected ACK 
		if((state == State.SYN_SENT || state == State.SYN_RECEIVED) && hs.getPeer().equals(currentPeer)) {
			handshakeACKs.add(hs);
		}
	}
	
	private Participant pickRandomSyn() {
	
		int tickCount = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		// Filter eligible SYNs (those that were received in the past, current tick is excluded)
		List<Handshake> eligibleSYNs = new ArrayList<>();
		for(Handshake hs : handshakeSYNs) {
			System.out.println(label + " received syn from " + hs.getPeer().getLabel());
			if(hs.getSentAt() < tickCount)
				if(protocolVariant.equals("TRANSITIVE_INTEREST_GOSSIP")  
						&& !(blocks.containsKey(this.id) 
						&& blocks.get(this.id).contains(hs.getPeer().getId()))) 
				
					eligibleSYNs.add(hs);
			
				else if(protocolVariant.equals("OPEN_GOSSIP"))
					eligibleSYNs.add(hs);
		}
		
//		if(blocks.get(id) != null)
//			for(PublicKey b : blocks.get(id))
//				System.out.println(label + " blocked " + TopologyManager.getParticipantById(b).getLabel());
//		
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
	
	/*
	 * Common method to both protocols to establish a connection 
	 * at the end of a succesfful handshake
	 */
	private boolean establishConnection() {
		if(handshakeACKs.size() == 0)
			return false;
		
		int tickCount = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		boolean established = false;
		
		Handshake ack = handshakeACKs.get(0);
		
		if(ack.getSentAt() < tickCount) {
			
			if(ack.getType() == Handshake.SYN_ACK && state == State.SYN_SENT) {
				currentPeer.onHandshakeACK(new Handshake(tickCount, Handshake.ACK, this));
				established = true;
			} else if(ack.getType() == Handshake.ACK && state == State.SYN_RECEIVED) {
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
	    	lastIndex = (lastIndex == null) ? -1 : lastIndex;
//	    	System.out.println(label  + " log len "  + log.size() + " for " 
//	    			+ TopologyManager.getParticipantById(news.get(0).getId()).getLabel());
	    	Integer previous = lastIndex == -1 ? 0 : log.get(lastIndex).hashCode();
	    	
//	    	System.out.println("last " + lastIndex + "idx " 
//	    	+ e.getIndex() + " prev " + e.getPrevious() + " prv " + previous + " " + e.isSignatureVerified());
//	    	System.out.println((e.getIndex().equals(lastIndex + 1)) 
//	    			+ "\n" + (previous.equals(e.getPrevious()))
//	    			+ "\n" + (e.isSignatureVerified()));
	    	
	    	if(e.getIndex().equals(lastIndex + 1)
	    			&& previous.equals(e.getPrevious())
	    			&& e.isSignatureVerified()) {
	    		System.out.println("Participant(" + label + "): applying update " + "#" + e.getIndex());
				
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
		frontier.remove(id);
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
	 * get the current frontier of my events
	 */
	public Integer getLocalLogLength() {
		if(getLogById(this.id) != null)
			return getLogById(this.id).size();
		else
			return 0;
	}
	
	/*
	 * get the set of events that happened after frontier
	 */
	private Map<PublicKey, List<Event>> getEventsSince(Map<PublicKey, Integer> frontier) {
		
		Map<PublicKey, List<Event>> events = new HashMap<>(); 
		
		for(Entry<PublicKey, Integer> entry : frontier.entrySet()) {
			PublicKey tempId = entry.getKey();
			Integer tempSince = entry.getValue();
//			if(tempId.equals(id))
//				System.out.println(label + " temp since " + tempSince);
			List<Event> log = getLogById(tempId);
			List<Event> eventsById = new ArrayList<>();
			for(Event e : log) {
				if(e.getIndex() > tempSince) {
					eventsById.add(e);
				}
			}
			//System.out.println("events by id size" + eventsById.size());
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
			//System.out.println("news by id size" + news.get(tempId).size());
			List<Event> newsById = news.get(tempId); //new events
			//List<Event> log = get(tempId); //target log for append operations
			updateLog(newsById);
			
			//update participant still alive
			int tickCount = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
			view.updateLastSeen(tempId, tickCount);
		}
	}
	
	/*
	 * calculate the percentage of received logs w.r.t the global number of logs
	 */
	public void calcLocalMissingUpdateRatio(Map<PublicKey, Integer> globalFrontier) {
		double totalOfLogs = 0;
		double myLogs = 0;
		
		for(Map.Entry<PublicKey, Integer> entry : globalFrontier.entrySet()) {
			if(!blocks.containsKey(this.id) || !blocks.get(this.id).contains(entry.getKey())) {

				if(entry.getValue() >= 0) {
					totalOfLogs += entry.getValue();
					
					if(getStoreIds().contains(entry.getKey())) {
						myLogs += getLogById(entry.getKey()).size();
						
						if(getLogById(entry.getKey()).size() < entry.getValue()) {
							System.out.println(label + " missing updates for participant " 
									+ TopologyManager.getParticipantById(entry.getKey()).getLabel());
						}
					}	
				}
			}
		}
		
//		for(Map.Entry<PublicKey, Integer> entry : this.frontier.entrySet()) {
//			if(entry.getValue() >= 0 && globalFrontier.containsKey(entry.getKey()))
//				myLogs += entry.getValue();
//		}
		
		System.out.println("MYLOGS: " + myLogs + "    TOTAL_LOGS: " + totalOfLogs);
		if(blocks.containsKey(this.id))
			System.out.println("BLOCKED_LOCALLY: " + blocks.get(this.id).size());

		
		if(totalOfLogs > 0)
			this.logPercentage = (myLogs / totalOfLogs)*100;
		else
			this.logPercentage = 0;
	}
	
	/*
	 * ****************************************************
	 * ********** TRANSITIVE-INTEREST OPERATIONS **********
	 * ****************************************************
	 */
	
	/*
	 * publicly record in log active interest in id
	 */
	
	
	private void follow(PublicKey targetId) {
		System.out.println("Participant(" + label + "): following " 
			+ TopologyManager.getParticipantById(targetId).getLabel());
		
		appendToLog(new Interest(targetId, Interest.Type.FOLLOW));
		addToFollows(this.id, targetId);
	}
	
	private void unfollow(PublicKey targetId) {
		System.out.println("Participant(" + label + "): unfollowing " 
				+ TopologyManager.getParticipantById(targetId).getLabel());
			
		appendToLog(new Interest(targetId, Interest.Type.UNFOLLOW));
		removeFromFollows(this.id, targetId);
	}
	
	private void block(PublicKey targetId) {
		System.out.println("Participant(" + label + "): blocking " 
				+ TopologyManager.getParticipantById(targetId).getLabel());
			
		appendToLog(new Interest(targetId, Interest.Type.BLOCK));
		addToBlocks(this.id, targetId);
	}
	
	private void unblock(PublicKey targetId) {
		System.out.println("Participant(" + label + "): unblocking " 
				+ TopologyManager.getParticipantById(targetId).getLabel());
			
		appendToLog(new Interest(targetId, Interest.Type.UNBLOCK));
		removeFromBlocks(this.id, targetId);
	}
	
	
	private void updateInterests(PublicKey id, List<Event> news) {
		for(Event e : news) {
			if(e.getContent() instanceof Interest) {
				Interest interest = (Interest)e.getContent();
				
				if(interest.getType() == Interest.Type.FOLLOW) {
					addToFollows(id, interest.getTargetId());
				}
				if(interest.getType() == Interest.Type.UNFOLLOW) {
					removeFromFollows(id, interest.getTargetId());
				}
				if(interest.getType() == Interest.Type.BLOCK) {
					addToBlocks(id, interest.getTargetId());
				}
				if(interest.getType() == Interest.Type.UNBLOCK) {
					removeFromBlocks(id, interest.getTargetId());
				}
			}
		}
	}
	
	
	// Note up that id follows target; if target is blocked, unblock
	private void addToFollows(PublicKey id, PublicKey targetId) {
		if(id != targetId || (id != this.id && this.id != targetId)) {
			if(blocks.containsKey(id) && blocks.get(id).contains(targetId)) {
				//unblock(targetId);
			}
			if(!follows.containsKey(id)) {
				follows.put(id, new ArrayList<>());
			}
					
			if(!follows.get(id).contains(targetId)) {
				follows.get(id).add(targetId);
			}
		}
	}
	
	// Note up that id does not follow target anymore
	private void removeFromFollows(PublicKey id, PublicKey targetId) {
		if(follows.containsKey(id) && follows.get(id).contains(targetId)) {
			follows.get(id).remove(targetId);
		}
	}
	
	// Note up that id blocks target; if target is followed, unfollow
	private void addToBlocks(PublicKey id, PublicKey targetId) {
		if(id != targetId || (id != this.id && this.id != targetId)) {
			if(follows.containsKey(id) && follows.get(id).contains(targetId)) {
				//unfollow(targetId);
			}
			if(!blocks.containsKey(id)) {
				blocks.put(id, new ArrayList<>());
			}
			
			if(!blocks.get(id).contains(targetId)) {
				blocks.get(id).add(targetId);
			}
			
			//if i am blocking someone directly, remove the log
			if(id == this.id) {
				removeLogFromStore(targetId);
			}
		}
	}
	
	//Note up that id odes not block target anymore
	private void removeFromBlocks(PublicKey id, PublicKey targetId) {
		if(blocks.containsKey(id) && blocks.get(id).contains(targetId)) {
			blocks.get(id).remove(targetId);
		}
	}
	
	private PublicKey pickRandomFollowing() {
		if(follows.containsKey(this.id) && !follows.get(this.id).isEmpty()) {
			int index = RandomHelper.nextIntFromTo(0, follows.get(this.id).size() - 1);
			return follows.get(this.id).get(index);
		} else {
			return null;
		}
	}
	
	private Participant pickRandomTransitivelyFollowed(PublicKey friendId) {
		if(follows.containsKey(friendId) && !follows.get(friendId).isEmpty()) {
			int index = RandomHelper.nextIntFromTo(0, follows.get(friendId).size() - 1);
			return TopologyManager.getParticipantById(follows.get(friendId).get(index));
		} else {
			return null;
		}
	}
	
	private Participant pickRandomTransitivelyBlocked(PublicKey friendId) {
		if(blocks.containsKey(friendId) && !blocks.get(friendId).isEmpty()) {
			int index = RandomHelper.nextIntFromTo(0, blocks.get(friendId).size() - 1);
			return TopologyManager.getParticipantById(blocks.get(friendId).get(index));
		} else {
			return null;
		}
	}
	
	/*
	 * At each tick, the participant might crash with a given probability.
	 * The priority of this method is random, therefore it might occur
	 * at different times with respect to the other scheduled methods.
	 */
	@ScheduledMethod(start=1, interval=1) //random priority by default
	public void crash() {
		double coinToss = RandomHelper.nextDoubleFromTo(0, 1);
		if(coinToss <= Options.CRASH_PROBABILITY) {
			
			System.out.println("Participant(" + label + ") crashed, removing it from the context...");
			crashed = true;
			TopologyManager.removeParticipant(this);
		}
	}
	
	@ScheduledMethod(start=200, interval=200, priority=20) 
	public void cleanUpView() {
		int tickCount = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		view.cleanUp(tickCount, label);
	}
	
//	private PublicKey pickRandomParticipant() {
//		Context<Object> context = ContextUtils.getContext(this);
//		List<Participant> tempParticipants = new ArrayList<>();
//		
//		for(Object obj : context) {
//			if(obj instanceof Participant) {
//				if(!obj.equals(this)) {
//					tempParticipants.add((Participant)obj);
//				}
//			}
//		}
//		
//		return tempParticipants.get(RandomHelper.nextIntFromTo(0, tempParticipants.size())).getId();
//	}
	
	public View getView() {
		return view;
	}
	
	public void setView(View view) {
		this.view = view;
	}
	
	public State getState() {
		return state;
	}
	
	public Participant getCurrentPeer() {
		return currentPeer;
	}
	
	public String getLabel() {
		return label;
	}
	
	public String getPercentage() {
		DecimalFormat df = new DecimalFormat("#");
		return (df.format(logPercentage));
	}
	
	public PublicKey getId() {
		return id;
	}
	
	public int getFailedConnections() {
		return failedConnections;
	}
	
	public void setFailedConnections(int value) {
		this.failedConnections = value;
	}
	
	public int getSucceededConnections() {
		return succeededConnections;
	}
	
	public void setSucceededConnections(int value) {
		this.succeededConnections = value;
	}
	
	public int getNumberOfNews() {
		return this.numberOfNews;
	}
	
	public void setNumberOfNews(int value) {
		this.numberOfNews = value;
	}
	
	public double getPercentageValue() {
		return logPercentage;
	}
	
	public int getIsSending() {
		return isSending;
	}
	
	@Override
	public boolean equals(Object obj) { 
        if (obj == this) { 
            return true; 
        } 
  
        if (obj == null || !(obj instanceof Participant)) { 
            return false; 
        } 
          
        // typecast o to Complex so that we can compare data members  
        Participant p = (Participant) obj; 
        
        if(p.getId().equals(this.id))
        	return true;
        else
        	return false;
        		
	}
	
	public boolean isCrashed() {
		return crashed;
	}
	
	public boolean hasLatencyEvent(Participant p) {
		return this.store.containsKey(p.getId());
	}
	
}

