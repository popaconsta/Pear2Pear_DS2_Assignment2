package pear2Pear_DS2_Assignment2;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.List;

import Utils.*;
import agents.Participant;
import repast.simphony.context.Context;
import repast.simphony.context.space.continuous.ContinuousSpaceFactory;
import repast.simphony.context.space.continuous.ContinuousSpaceFactoryFinder;
import repast.simphony.context.space.graph.NetworkBuilder;
import repast.simphony.context.space.grid.GridFactory;
import repast.simphony.context.space.grid.GridFactoryFinder;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.parameter.Parameters;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.NdPoint;
import repast.simphony.space.continuous.RandomCartesianAdder;
import repast.simphony.space.continuous.SimpleCartesianAdder;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridBuilderParameters;
import repast.simphony.space.grid.SimpleGridAdder;
import repast.simphony.space.grid.StrictBorders;
import security.KeyManager;

import Utils.Options;

public class DemoBuilder implements ContextBuilder<Object> {
	
	public static List<NdPoint> availableSlots;

	@Override
	public Context build(Context<Object> context) {
		context.setId("Pear2Pear_DS2_Assignment2");
		
		//Load the parameters
		Options.load();
		
		//Clearing files
		DataCollector.clearFiles();
		
		/*
		 * This network is used for visualizing the connection between 
		 * participants who attempt handshakes with each other. 
		 * When a participant sends a SYN/SYN_ACK/ACK message, the link is updated
		 */
		NetworkBuilder<Object> netBuilder = new NetworkBuilder<Object>("handshake network", context, false);
		netBuilder.buildNetwork();
		
		//Generate public and private keys
        try {
        	KeyManager.initialize(1024, Options.MAX_PARTICIPANT_COUNT);
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            System.out.println(e.getMessage());
        }
		
		//Position the nodes in a specific way to create the needed topology
		TopologyManager.initialize(context);
		TopologyManager.buildTopology();
		

        //Choose 2 nodes which are relevant for our benchmarks
        selectNodesForBroadcastLatency(context, TopologyManager.getSpace());

		return context ;
	}
	
	
	
	public void selectNodesForBroadcastLatency(Context<Object> context, ContinuousSpace<Object> space) {
//		int nodeA = 0;
//		int nodeB = 0;
//		double maxDistance = 0;
//		double thisDistance;
//		
//		for (Object obj : context) {
//			for (Object obj2 : context) {
//				NdPoint pt = space.getLocation(obj);
//				NdPoint pt2 = space.getLocation(obj2);
//				
//				thisDistance = space.getDistance(pt, pt2);
//				if(thisDistance > maxDistance) {
//					nodeA = ((Participant)obj).id;
//					nodeB = ((Participant)obj2).id;
//					maxDistance = thisDistance;
//				}
//				
//			}
//		}
//		
//		Options.NODE_A_BROADCAST = nodeA;
//		Options.NODE_B_BROADCAST = nodeB;
//		System.out.println(nodeA + " " + nodeB);
		
	}

}
