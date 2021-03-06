package models.database.HotDatabase;

import models.database.IDatabase;
import models.database.IQuestionDatabase;
import models.database.ITagDatabase;
import models.database.IUserDatabase;

/**
 * Provides a Database that is kept entirely in Memory.
 * @author aaron
 *
 */
public class HotDatabase implements IDatabase {
	static final HotQuestionDatabase questions = new HotQuestionDatabase();
	static final HotUserDatabase users = new HotUserDatabase();
	static final HotTagDatabase tags = new HotTagDatabase();


	public IQuestionDatabase questions() {
		return questions;
	}

	public IUserDatabase users() {
		return users;
	}

	public ITagDatabase tags() {
		return tags;
	}
}
