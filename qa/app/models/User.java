package models;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import models.database.Database;
import models.helpers.Filter;

/**
 * A user with a name. Can contain {@link Item}s i.e. {@link Question}s,
 * {@link Answer}s, {@link Comment}s and {@link Vote}s. When deleted, the
 * <code>User</code> requests all his {@link Item}s to delete themselves.
 * 
 * @author Simon Marti
 * @author Mirco Kocher
 * 
 */
public class User implements IObserver {

	private final String name;
	private final String password;
	private String email;
	private final HashSet<Item> items;
	private String fullname;
	protected Date dateOfBirth;
	private String website;
	private String profession;
	private String employer;
	private String biography;
	private String statustext;
	private boolean isBlocked = false;
	private boolean isModerator = false;
	
	public static final String DATE_FORMAT_CH = "dd.MM.yyyy";
	public static final String DATE_FORMAT_US = "MM/dd/yyyy";
	public static final String DATE_FORMAT_ISO = "yyyy-MM-dd";


	/**
	 * Creates a <code>User</code> with a given name.
	 * 
	 * @param name the name of the <code>User</code>
	 */
	public User(String name, String password) {
		this.name = name;
		this.password = encrypt(password);
		this.items = new HashSet<Item>();
	}
	
	public boolean canEdit(Entry entry) {
		return (entry.owner() == this && !this.isBlocked()) || this.isModerator();
	}

	/**
	 * Gets the name of the <code>User</code>.
	 * 
	 * @return name of the <code>User</code>
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Encrypt the password with SHA-1
	 * 
	 * @param password
	 * @return the encrypted password
	 */
	public static String encrypt(String password) {
		try {
			MessageDigest m = MessageDigest.getInstance("SHA-1");
			return new BigInteger(1, m.digest(password.getBytes()))
					.toString(16);
		} catch (NoSuchAlgorithmException e) {
			return password;
		}
	}

	/**
	 * Encrypt the password and check if it is the same as the stored one.
	 * 
	 * @param passwort
	 * @return true if the password is right
	 */
	public boolean checkPW(String password) {
		return this.password.equals(encrypt(password));
	}

	/**
	 * Check an email-address to be valid.
	 * 
	 * @param email
	 * @return true if the email is valid.
	 */
	public static boolean checkEmail(String email) {
		return email.matches("\\S+@(?:[A-Za-z0-9-]+\\.)+\\w{2,4}");

	}

	/**
	 * Registers an {@link Item} which should be deleted in case the
	 * <code>User</code> gets deleted.
	 * 
	 * @param item the {@link Item} to register
	 */
	public void registerItem(Item item) {
		this.items.add(item);
	}

	/**
	 * Checks at Sign Up if the entered username is available. This way we can
	 * avoid having two User called "SoMeThinG" and "SoMetHinG" which might be
	 * hard to distinguish
	 * 
	 * @param username
	 * @return true if the username is available.
	 */
	public static boolean isAvailable(String username) {
		return (Database.get().users().get(username.toLowerCase()) == null);
	}

	/**
	 * Causes the <code>User</code> to delete all his {@link Item}s.
	 */
	public void delete() {
		// operate on a clone to prevent a ConcurrentModificationException
		HashSet<Item> clone = (HashSet<Item>) this.items.clone();
		for (Item item : clone)
			item.unregister();
		this.items.clear();
		Database.get().users().remove(this.name);
	}

	/**
	 * Unregisters an {@link Item} which has been deleted.
	 * 
	 * @param item the {@link Item} to unregister
	 */
	public void unregister(Item item) {
		this.items.remove(item);
	}

	/**
	 * Checks if an {@link Item} is registered and therefore owned by a
	 * <code>User</code>.
	 * 
	 * @param item the {@link Item}to check
	 * @return true if the {@link Item} is registered
	 */
	public boolean hasItem(Item item) {
		return this.items.contains(item);
	}

	/**
	 * The amount of Comments, Answers and Questions the <code>User</code> has
	 * posted in the last 60 Minutes.
	 * 
	 * @return The amount of Comments, Answers and Questions for this
	 *         <code>User</code> in this Hour.
	 */
	public int howManyItemsPerHour() {
		Date now = SystemInformation.get().now();
		int i = 0;
		for (Item item : this.items) {
			if ((now.getTime() - item.timestamp().getTime()) <= 60 * 60 * 1000) {
				i++;
			}
		}
		return i;
	}

	/**
	 * The <code>User</code> is a Cheater if over 50% of his votes is for the
	 * same <code>User</code>.
	 * 
	 * @return True if the <code>User</code> is supporting somebody.
	 */
	public boolean isMaybeCheater() {
		HashMap<User, Integer> votesForUser = new HashMap<User, Integer>();
		for (Item item : this.items) {
			if (item instanceof Vote && ((Vote) item).up()) {
				Integer count = votesForUser.get(item.owner());
				if (count == null)
					count = 0;
				votesForUser.put(item.owner(), count + 1);
			}
		}

		if (votesForUser.isEmpty())
			return false;

	    Integer maxCount = Collections.max(votesForUser.values());
		if (maxCount > 3 && maxCount / votesForUser.size() > 0.5) {
			this.setStatusMessage("User voted up somebody");
			this.setBlocked(true);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Anonymizes all questions, answers and comments by this user.
	 * 
	 * @param doAnswers - whether to anonymize this user's answers as well
	 * @param doComments - whether to anonymize this user's comments as well
	 */
	public void anonymize(boolean doAnswers, boolean doComments) {
		// operate on a clone to prevent a ConcurrentModificationException
		HashSet<Item> clone = (HashSet<Item>) this.items.clone();
		for (Item item : clone) {
			if (item instanceof Question || doAnswers && item instanceof Answer
					|| doComments && item instanceof Comment) {
				((Entry) item).anonymize();
				this.items.remove(item);
			}
		}
	}


	/**
	 * The <code>User</code> is a Spammer if he posts more than 30 comments,
	 * answers or questions in the last hour.
	 * 
	 * @return True if the <code>User</code> is a Spammer.
	 */
	public boolean isSpammer() {
		int number = this.howManyItemsPerHour();
		if (number >= 60) {
			this.setStatusMessage("User is a Spammer");
			this.setBlocked(true);
			return true;
		}
		return false;
	}


	/**
	 * Set the <code>User</code> as a Cheater if he spams the Site or supports
	 * somebody.
	 * 
	 */
	public boolean isCheating() {
		return (isSpammer() || isMaybeCheater());
	}

	/**
	 * Calculates the age of the <code>User</code> in years.
	 * 
	 * @return age of the <code>User</code>
	 */
	private int age() {
		Date now = SystemInformation.get().now();
		if (dateOfBirth != null) {
			long age = now.getTime() - dateOfBirth.getTime();
			return (int) (age / ((long) 1000 * 3600 * 24 * 365));
		} else
			return (0);
	}

	/**
	 * Turns the Date object d into a String using the format given in the
	 * constant DATE_FORMAT.
	 */
	private String dateToString(Date d) {
		if (d != null) {
			SimpleDateFormat fmt = new SimpleDateFormat(DATE_FORMAT_CH);
			return fmt.format(d);
		} else
			return null;
	}

	/**
	 * Turns the String object s into a Date assuming the format given in the
	 * constant DATE_FORMAT.
	 * 
	 * @throws ParseException
	 */
	private Date stringToDate(String s) throws ParseException {
		if (Pattern.matches("\\d{1,2}\\.\\d{1,2}\\.\\d{4}", s)) {
			SimpleDateFormat fmt = new SimpleDateFormat(DATE_FORMAT_CH);
			return fmt.parse(s);
		} else if (Pattern.matches("\\d{1,2}/\\d{1,2}/\\d{4}", s)) {
			SimpleDateFormat fmt = new SimpleDateFormat(DATE_FORMAT_US);
			return fmt.parse(s);
		} else if (Pattern.matches("\\d{4}-\\d{1,2}-\\d{1,2}", s)) {
			SimpleDateFormat fmt = new SimpleDateFormat(DATE_FORMAT_ISO);
			return fmt.parse(s);
		} else
			return (null);
	}

	/* Getter and Setter for profile data */

	public void setEmail(String email) {
		this.email = email;
	}

	public String getEmail() {
		return this.email;
	}

	public void setFullname(String fullname) {
		this.fullname = fullname;
	}

	public String getFullname() {
		return this.fullname;
	}

	public void setDateOfBirth(String birthday) throws ParseException {
		this.dateOfBirth = stringToDate(birthday);
	}

	public String getDateOfBirth() {
		return this.dateToString(dateOfBirth);
	}

	public int getAge() {
		return this.age();
	}

	public void setWebsite(String website) {
		this.website = website;
	}

	public String getWebsite() {
		return this.website;
	}

	public void setProfession(String profession) {
		this.profession = profession;
	}

	public String getProfession() {
		return this.profession;
	}

	public void setEmployer(String employer) {
		this.employer = employer;
	}

	public String getEmployer() {
		return this.employer;
	}

	public void setBiography(String biography) {
		this.biography = biography;
	}

	public String getBiography() {
		return this.biography;
	}

	public String getSHA1Password() {
		return this.password;
	}

	public String getStatusMessage() {
		return this.statustext;
	}

	public void setStatusMessage(String blockreason) {
		this.statustext = blockreason;
	}
	
	public void setBlocked(Boolean block) {
		if (block == false)
			this.setStatusMessage("");
		this.isBlocked = block;
	}
	
	public boolean isBlocked() {
		return this.isBlocked;
	}


	public boolean isModerator() {
		return this.isModerator;
	}

	public void setModerator(Boolean mod) {
		this.isModerator = mod;
	}
	/**
	 * Start observing changes for an entry (e.g. new answers to a question).
	 * 
	 * @param what the entry to watch
	 */
	public void startObserving(IObservable what) {
		what.addObserver(this);
	}

	/**
	 * Checks if a specific entry is being observed for changes.
	 * 
	 * @param what the entry to check
	 */
	public boolean isObserving(IObservable what) {
		return what.hasObserver(this);
	}
	
	public static User get(String name) {
		return Database.get().users().get(name.toLowerCase());
	}

	/**
	 * Stop observing changes for an entry (e.g. new answers to a question).
	 * 
	 * @param what the entry to unwatch
	 */
	public void stopObserving(IObservable what) {
		what.removeObserver(this);
	}

	/**
	 * @see models.IObserver#observe(models.IObservable, java.lang.Object)
	 */
	public void observe(IObservable o, Object arg) {
		if (o instanceof Question && arg instanceof Answer
				&& ((Answer) arg).owner() != this)
			new Notification(this, (Answer) arg);
	}

	/**
	 * Registers a new <code>User</code> to the database.
	 * 
	 * @param username
	 * @param password of the <code>User</code>
	 * @return user
	 */

	/**
	 * Get a List of the last three <code>Question</code>s of this <code>User</code>.
	 * 
	 * @return List<Question> The last three <code>Question</code>s of this <code>User</code>
	 */
	public List<Question> getRecentQuestions() {
		List<Question> recentQuestions = this.getQuestions();
		Collections.sort(recentQuestions, new Comparator() {
			public int compare(Object o1, Object o2) {
				return ((Item) o2).timestamp().compareTo(((Item) o1).timestamp());
			}
		}); 
		if (recentQuestions.size() > 3)
			return recentQuestions.subList(0, 3);
		return recentQuestions;
	}

	/**
	 * Get a List of the last three <code>Answer</code>s of this <code>User</code>.
	 * 
	 * @return List<Answer> The last three <code>Answer</code>s of this <code>User</code>
	 */
	public List<Answer> getRecentAnswers() {
		List<Answer> recentAnswers = this.getAnswers();
		Collections.sort(recentAnswers, new Comparator() {
			public int compare(Object o1, Object o2) {
				return ((Item) o2).timestamp().compareTo(((Item) o1).timestamp());
			}
		}); 
		if (recentAnswers.size() > 3)
			return recentAnswers.subList(0, 3);
		return recentAnswers;
	}
	
	/**
	 * Get a List of the last three <code>Comment</code>s of this <code>User</code>.
	 * 
	 * @return List<Comment> The last three <code>Comment</code>s of this <code>User</code>
	 */
	public List<Comment> getRecentComments() {
		List<Comment> recentComments = this.getComments();
		Collections.sort(recentComments, new Comparator() {
			public int compare(Object o1, Object o2) {
				return ((Item) o2).timestamp().compareTo(((Item) o1).timestamp());
			}
		}); 
		if (recentComments.size() > 3)
			return recentComments.subList(0, 3);
		return recentComments;
	}

	/*
	 * Interface to gather statistical data
	 */

	public static int getUserCount() {
		return Database.get().users().count();
	}

	/**
	 * Get a sorted ArrayList of all <code>Questions</code>s of this <code>User</code>.
	 * 
	 * @return ArrayList<Question> All questions of this <code>User</code>
	 */
	public ArrayList<Question> getQuestions() {
		return this.getItemsByType(Question.class, null);
	}

	/**
	 * Get a sorted ArrayList of all <code>Answer</code>s of this <code>User</code>.
	 * 
	 * @return ArrayList<Answer> All <code>Answer</code>s of this <code>User</code>
	 */
	public ArrayList<Answer> getAnswers() {
		return this.getItemsByType(Answer.class, null);
	}
	
	/**
	 * Get a sorted ArrayList of all <code>Comment</code>s of this <code>User</code>
	 * 
	 * @return ArrayList<Comment> All <code>Comments</code>s of this <code>User</code>
	 */
	public ArrayList<Comment> getComments() {
		return this.getItemsByType(Comment.class, null);
	}

	/**
	 * Get an ArrayList of all best rated answers
	 * 
	 * @return List<Answer> All best rated answers
	 */
	public List<Answer> bestAnswers() {
		return this.getItemsByType(Answer.class, new Filter<Answer, Boolean>() {
			public Boolean visit(Answer a) {
				return a.isBestAnswer();
			}
		});
	}

	/**
	 * Get an ArrayList of all highRated answers
	 * 
	 * @return List<Answer> All high rated answers
	 */
	public List<Answer> highRatedAnswers() {
		return this.getItemsByType(Answer.class, new Filter<Answer, Boolean>() {
			public Boolean visit(Answer a) {
				return a.isHighRated();
			}
		});
	}

	/**
	 * Get an ArrayList of all notifications of this user, sorted most-recent
	 * one first and optionally fulfilling one filter criterion.
	 * 
	 * @param filter
	 *            an optional name of a filter method (e.g. "isNew")
	 * @return ArrayList<Notification> All notifications of this user
	 */
	protected ArrayList<Notification> getAllNotifications(Filter filter) {
		ArrayList<Notification> result = new ArrayList<Notification>();
		/*
		 * Hack: remove all notifications to deleted answers
		 * 
		 * unfortunately, there's currently no other way to achieve this, as
		 * there is no global list of all existing notifications nor an easy way
		 * to register all users for observing the deletion of answers (because
		 * there's no global list of all existing users, either)
		 */
		ArrayList<Notification> notifications = this.getItemsByType(
				Notification.class, filter);
		for (Notification n : notifications) {
			if (n.getAbout() instanceof Answer) {
				Answer answer = (Answer) n.getAbout();
				if (answer.getQuestion() != null)
					result.add(n);
				else
					n.unregister();
			}
		}
		return result;
	}

	/**
	 * Get an ArrayList of all notifications of this user, sorted most-recent
	 * one first.
	 * 
	 * @return ArrayList<Notification> All notifications of this user
	 */
	public ArrayList<Notification> getNotifications() {
		return this.getAllNotifications(null);
	}

	/**
	 * Get an ArrayList of all unread notifications of this user
	 * 
	 * @return the unread notifications
	 */
	public ArrayList<Notification> getNewNotifications() {
		return this.getAllNotifications(new Filter<Notification, Boolean>() {
			public Boolean visit(Notification n) {
				return n.isNew();
			}
		});
	}

	/**
	 * Gets the most recent unread notification, if there is any very recent one
	 * 
	 * @return a very recent notification (or null, if there isn't any)
	 */
	public Notification getVeryRecentNewNotification() {
		for (Notification n : this.getNewNotifications())
			if (n.isVeryRecent())
				return n;
		return null;
	}

	/**
	 * Gets a notification by its id value.
	 * 
	 * NOTE: slightly hacky since we don't track notifications in a separate
	 * IDTable but in this.items like everything else - this should get fixed
	 * once we migrate to using a real DB.
	 * 
	 * @param id
	 *            the notification's id
	 * @return a notification with the given id
	 */
	public Notification getNotification(int id) {
		for (Notification n : this.getNotifications())
			if (n.getID() == id)
				return n;
		return null;
	}

	/**
	 * Get an ArrayList of all items of this user being an instance of a
	 * specific type and optionally fulfilling an additional filter criterion.
	 * 
	 * @param type
	 *            the type
	 * @param filter
	 *            an optional name of a filter method which has to be available
	 *            on all objects, must not need any arguments and must return a
	 *            boolean value
	 * @return ArrayList All type-items of this user
	 */
	protected ArrayList getItemsByType(Class type, Filter filter) {
		ArrayList items = new ArrayList();
		for (Item item : this.items)
			if (type.isInstance(item)
					&& (filter == null || (Boolean) filter.visit(item)))
				items.add(item);
		Collections.sort(items);
		return items;
	}
}
