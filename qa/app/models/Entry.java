package models;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

/**
 * An {@link Item} which has a content and can be voted up and down.
 * 
 * @author Simon Marti
 * @author Mirco Kocher
 */
public abstract class Entry extends Item implements Comparable {

	private final String content;
	private HashMap<String, Vote> votes;

	/**
	 * Create an <code>Entry</code>.
	 * 
	 * @param owner the {@link User} who owns the <code>Entry</code>
	 * @param content the content of the <code>Entry</code>
	 */
	public Entry(User owner, String content) {
		super(owner);
		this.content = content;
		this.votes = new HashMap<String,Vote>();
	}

	/**
	 * Unregisters a deleted {@link Comment} to its {@link Entry}.
	 * 
	 * @param comment the <code> Comment </code> to be unregistered
	 */
	public abstract void unregister(Comment comment);
	
	/**
	 * Unregisters the <code>Entry</code> if it gets deleted.
	 */
	@Override
	public void unregister() {
		this.unregisterVotes();
		this.unregisterUser();
	}

	/**
	 * Delete all {@link Vote}s if the <code>Entry</code> gets deleted.
	 */
	protected void unregisterVotes() {
		Collection<Vote> votes = this.votes.values();
		this.votes = new HashMap();
		for (Vote vote : votes)
			vote.unregister();
	}

	/**
	 * Unregisters a deleted {@link Vote}.
	 * 
	 * @param vote the {@link Vote} to unregister
	 */
	public void unregister(Vote vote) {
		this.votes.remove(vote.owner().getName());
	}

	/**

	 * Get the content of an <code>Entry</code>.
	 * 
	 * @return the content of the <code>Entry</code>
	 */
	public String content() {
		return this.content;
	}

	/**
	 * Count all positive {@link Vote}s on an <code>Entry</code>.
	 * 
	 * @return number of positive {@link Vote}s
	 */
	public int upVotes() {
		return this.countVotes(true);
	}

	/**
	 * Count all negative {@link Vote}s on an <code>Entry</code>.
	 * 
	 * @return number of negative {@link Vote}s
	 */
	public int downVotes() {
		return this.countVotes(false);
	}

	/**
	 * Get the current rating of the <code>Entry</code>.
	 * 
	 * @return rating as an <code>Integer</code>
	 */
	public int rating() {
		return this.upVotes() - this.downVotes();
	}

	/**
	 * Compares this <code>Entry</code> with another one with respect to their
	 * ratings.
	 * 
	 * @return comparison result (-1 = this Entry has more upVotes)
	 */
	public int compareTo(Object o) {
		return ((Entry) o).rating() - this.rating();
	}
	
	/**
	 * Counts the number of <code>Votes</code> of an <code>Entry</code>.
	 * 
	 * @param up boolean whether there is a <code>Vote</code> to this <code>Entry</code> or not
	 * @return counter number of <code>Votes</code>
	 */
	private int countVotes(boolean up) {
		int counter = 0;
		for (Vote vote : this.votes.values())
			if (vote.up() == up)
				counter++;
		return counter;
	}

	/**
	 * Vote an <code>Entry</code> up.
	 * 
	 * @param user the {@link User} who voted
	 * @return the {@link Vote}
	 */
	public Vote voteUp(User user) {
		return this.vote(user, true);
	}

	/**
	 * Vote an <code>Entry</code> down.
	 * 
	 * @param user the {@link User} who voted
	 * @return the {@link Vote}
	 */
	public Vote voteDown(User user) {
		return this.vote(user, false);
	}

	/**
	 * Let an <code>User</code> vote for an <code>Entry</code>.
	 * 
	 * @param user who is voting
	 * @return vote of the <code>User</code>
	 */
	private Vote vote(User user, boolean up) {
		if (user == this.owner())
			return null;
		if (this.votes.containsKey(user.getName()))
			this.votes.get(user.getName()).unregister();
		Vote vote = new Vote(user, this, up);
		this.votes.put(user.getName(), vote);
		return vote;
	}
	
	 /**
	  * Turns this Entry into an anonymous (user-less) one.
	  */
	 public void anonymize() {
		 this.unregisterUser();
	 }

	/**
	 * Produces a one-line summary of an Entry: the first 35 to 45 characters,
	 * if possible cut off at a word boundary, and an ellipsis, if the content
	 * is longer.
	 * 
	 * @return a one-line summary of an <code>Entry</code>.
	 * */
	public String summary() {
		return this.content.replaceAll("\\s+", " ").replaceFirst(
				"^(.{35}\\S{0,9} ?).{5,}", "$1...");
	}

	/**
	 * Get all <code>Votes</code>.
	 * 
	 * @return votes
	 */
	public Collection<Vote> getVotes() {
		return votes.values();
	}
	
	public String toString() {
		if (content.length() > 15) {
			return "Entry("+content.substring(0, 20)+"...)";
		}
		else {
			return "Entry("+content+")";
		}
	}

}
