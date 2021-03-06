	package tel.discord.rtab.games;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import tel.discord.rtab.Achievement;

public class DealOrNoDeal extends MiniGameWrapper
{
	static final String NAME = "Deal or No Deal";
	static final String SHORT_NAME = "Deal";
	static final boolean BONUS = false;
	List<Integer> VALUE_LIST = Arrays.asList(1,2,5,10,50,100,500,1000,2500,5000,7500, //Blues
			10_000,30_000,50_000,100_000,150_000,200_000,350_000,500_000,750_000,1_000_000,2_500_000); //Reds
	LinkedList<Integer> values = new LinkedList<>();
	int offer;
	int prizeWon;
	int casesLeft;
	boolean accept; //Accepting the Offer

	@Override
	void startGame()
	{
		casesLeft = VALUE_LIST.size();
		offer = 0;
		accept = false;
		//Multiply each value, EXCEPT the $1, by the base multiplier
		for(int i = 1; i < VALUE_LIST.size(); i++)
		{
			VALUE_LIST.set(i, applyBaseMultiplier(VALUE_LIST.get(i)));
		}
		//Load up the boxes and shuffle them
		values.clear();
		values.addAll(VALUE_LIST);
		Collections.shuffle(values);
		//Give instructions
		LinkedList<String> output = new LinkedList<>();
		output.add("In Deal or No Deal, there are 22 boxes, "
				+ String.format("each holding an amount of money from $1 to $%,d.",applyBaseMultiplier(2_500_000)));
		output.add("One of these boxes is 'yours', and if you refuse all the offers you win the contents of that box.");
		output.add("We open the other boxes one by one to find out which values *aren't* in your own box.");
		output.add("The first offer comes after five boxes are opened, after which offers are received every three boxes.");
		output.add("If you take an offer at any time, you win that amount instead of the contents of the final box.");
		output.add("Best of luck, let's start the game...");
		sendSkippableMessages(output);
		output.clear();
		output.add(generateBoard());
		output.add("Opening five boxes...");
		for(int i=0; i<5; i++)
			output.add(openBox());
		output.add("...");
		output.add(generateOffer());
		output.add(generateBoard());
		output.add("Deal or No Deal?");
		sendMessages(output);
		getInput();
	}

	private String openBox()
	{
		casesLeft --;
		return String.format("$%,d!",values.pollFirst());
	}

	@Override
	void playNextTurn(String pick)
	{
		LinkedList<String> output = new LinkedList<>();
		String choice = pick.toUpperCase();
		choice = choice.replaceAll("\\s","");
		if(choice.equals("REFUSE") || choice.equals("NODEAL") || choice.equals("ND"))
		{
			output.add("NO DEAL!");
			if(casesLeft == 2)
			{
				output.add("Your box contains...");
				prizeWon = values.pollLast();
				output.add(String.format("$%,d!",prizeWon));
				accept = true;
			}
			else
			{
				output.add("Opening three boxes...");
				for(int i=0; i<3; i++)
					output.add(openBox());
				output.add("...");
				output.add(generateOffer());
				output.add(generateBoard());
				output.add("Deal or No Deal?");
			}
			sendMessages(output);
		}
		else if(choice.equals("ACCEPT") || choice.equals("DEAL") || choice.equals("D"))
		{
			accept = true;
			prizeWon = offer;
			output.add("It's a DONE DEAL!");
			output.add("Now for the proveout... (you can !skip this)");
			sendMessages(output);
			sendSkippableMessages(runProveout());
		}
		if(accept)
			awardMoneyWon(prizeWon);
		else
			getInput();
	}

	private String generateOffer()
	{
		//Generate "fair deal" and average
		int fairDeal = 0;
		int average = 0;
		for(int i : values)
		{
			fairDeal += Math.sqrt(i);
			average += i;
		}
		fairDeal /= casesLeft;
		average /= casesLeft;
		fairDeal = (int)Math.pow(fairDeal,2);
		//Check for dream finish achievement
		if(casesLeft == 2 && average >= applyBaseMultiplier(1_750_000) && !accept)
			Achievement.DEAL_JACKPOT.check(getCurrentPlayer());
		//Use the fair deal as the base of the offer, then add a portion of the average to it depending on round
		offer = fairDeal + ((average-fairDeal) * (20-casesLeft) / 40);
		//Add random factor: 0.90-1.10
		int multiplier = (int)((Math.random()*21) + 90);
		offer *= multiplier;
		offer /= 100;
		//Round it off
		if(offer > 250000)
			offer -= (offer%10000);
		else if(offer > 25000)
			offer -= (offer%1000);
		else if(offer > 2500)
			offer -= (offer%100);
		else if(offer > 250)
			offer -= (offer%10);
		//And format the result they want to see
		return String.format("BANK OFFER: $%,d",offer);
	}

	private String generateBoard() {
		StringBuilder output = new StringBuilder();
		output.append("```\n");
		//Header
		output.append("    DEAL OR NO DEAL    \n");
		if(offer > 0)
			output.append(String.format("   OFFER: $%,9d   \n",offer));
		output.append("\n");
		//Main board
		int nextValue = 0;
		for(int i=0; i<VALUE_LIST.size(); i++)
		{
			if(values.contains(VALUE_LIST.get(nextValue)))
			{
				output.append(String.format("$%,9d",VALUE_LIST.get(nextValue)));
			}
			else
			{
				output.append("          ");
			}
			//Order is 0, 11, 1, 12, ..., 9, 20, 10, 21
			nextValue += VALUE_LIST.size()/2;
			if(nextValue >= VALUE_LIST.size())
				nextValue -= VALUE_LIST.size() - 1;
			//Space appropriately
			output.append(i%2==0 ? "   " : "\n");
		}
		output.append("```");
		return output.toString();
	}
	
	private LinkedList<String> runProveout()
	{
		LinkedList<String> output = new LinkedList<String>();
		while(casesLeft > 2)
		{
			StringBuilder boxesOpened = new StringBuilder();
			for(int i=0; i<3; i++)
				boxesOpened.append(openBox()).append(" ");
			output.add(boxesOpened.toString());
			generateOffer();
			output.add(generateBoard());
		}
		output.add("Your box contained...");
		output.add(String.format("$%,d!",values.pollLast()));
		return output;
	}

	@Override
	String getBotPick() {
		//Chance to deal is based on offer as percent of average
		int totalValue = 0;
		for(int i : values)
			totalValue += i;
		double average = totalValue / casesLeft;
		double dealChance = offer / average;
		return (Math.random() < dealChance) ? "DEAL" : "NO DEAL";
	}

	@Override
	void abortGame()
	{
		//Take the deal
		awardMoneyWon(offer);
	}

	@Override
	public String getName()
	{
		return NAME;
	}
	@Override
	public String getShortName()
	{
		return SHORT_NAME;
	}
	@Override
	public boolean isBonus()
	{
		return BONUS;
	}
}
