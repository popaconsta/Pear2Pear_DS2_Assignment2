package Utils;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import au.com.bytecode.opencsv.*;

import communication.*;

public class DataCollector {

	public static void clearFiles() {
		try {
			//clear Connections
			FileWriter fw = new FileWriter("Connections.csv", false);
			PrintWriter pw = new PrintWriter(fw, false);
			pw.write("SConnections;FConnections;Tick" + '\n');
	        pw.flush();
	        pw.close();
	        fw.close();
	        
	        //clear NumberParticipants
	        FileWriter fw2 = new FileWriter("NumberParticipants.csv", false);
			PrintWriter pw2 = new PrintWriter(fw2, false);
			pw2.write("Count;Tick" + '\n');
	        pw2.flush();
	        pw2.close();
	        fw2.close();
	        
//	        
//	        
//	        //clear NumberOfNews
	        FileWriter fw3 = new FileWriter("NumberOfNews.csv", false);
			PrintWriter pw3 = new PrintWriter(fw3, false);
			pw3.write("News;Summation;Tick" + '\n');
	        pw3.flush();
	        pw3.close();
	        fw3.close();
//	        
	      //clear FreeBandwidth
	        FileWriter fw4 = new FileWriter("FreeBandwidth.csv", false);
			PrintWriter pw4 = new PrintWriter(fw4, false);
			pw4.write("Value;Tick" + '\n');
	        pw4.flush();
	        pw4.close();
	        fw4.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void saveConnections(int succeededConnections, int failedConnections, double tick) {
		CSVWriter writer;
		try {
			writer = new CSVWriter(new FileWriter("Connections.csv", true), ';', '\0');
			String[] entries = {succeededConnections + "", failedConnections + "", tick + ""};
			writer.writeNext(entries);
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void saveNumberOfParticipants(int participants, double tick) {
		CSVWriter writer;
		try {
			writer = new CSVWriter(new FileWriter("NumberParticipants.csv", true), ';', '\0');
			String[] entries = {participants + "", tick + ""};
			writer.writeNext(entries);
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void saveNumberOfNews(int news, int summation, double tick) {
		CSVWriter writer;
		try {
			writer = new CSVWriter(new FileWriter("NumberOfNews.csv", true), ';', '\0');
			String[] entries = {news + "", summation + "", tick + ""};
			writer.writeNext(entries);
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void saveFreeBandwidth(double freeBandwidth, double tick) {
		CSVWriter writer;
		try {
			writer = new CSVWriter(new FileWriter("FreeBandwidth.csv", true), ';', '\0');
			String[] entries = {freeBandwidth + "", tick + ""};
			writer.writeNext(entries);
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
