/*
 * Copyright (c) 2008 - 2013 10gen, Inc. <http://10gen.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb;

import org.mongodb.Codec;
import org.mongodb.CreateCollectionOptions;
import org.mongodb.Document;
import org.mongodb.MongoConnector;
import org.mongodb.MongoNamespace;
import org.mongodb.annotations.ThreadSafe;
import org.mongodb.command.Create;
import org.mongodb.command.DropDatabase;
import org.mongodb.command.GetLastError;
import org.mongodb.command.MongoCommand;
import org.mongodb.command.MongoCommandFailureException;
import org.mongodb.operation.MongoFind;
import org.mongodb.result.QueryResult;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.mongodb.DBObjects.toDocument;


@ThreadSafe
public class DB implements IDB {
    private final Mongo mongo;
    private final String name;
    private final ConcurrentHashMap<String, DBCollection> collectionCache;
    private final Bytes.OptionHolder optionHolder;
    private final Codec<Document> documentCodec;
    private volatile ReadPreference readPreference;
    private volatile WriteConcern writeConcern;


    DB(final Mongo mongo, final String dbName, final Codec<Document> documentCodec) {
        this.mongo = mongo;
        this.name = dbName;
        this.documentCodec = documentCodec;
        this.collectionCache = new ConcurrentHashMap<String, DBCollection>();
        this.optionHolder = new Bytes.OptionHolder(mongo.getOptionHolder());
    }

    /**
     * Gets the Mongo instance
     *
     * @return the mongo instance that this database was created from.
     */
    @Override
    public Mongo getMongo() {
        return mongo;
    }

    @Override
    public void setReadPreference(final ReadPreference readPreference) {
        this.readPreference = readPreference;
    }

    @Override
    public void setWriteConcern(final WriteConcern writeConcern) {
        this.writeConcern = writeConcern;
    }

    @Override
    public ReadPreference getReadPreference() {
        return readPreference != null ? readPreference : mongo.getReadPreference();
    }

    @Override
    public WriteConcern getWriteConcern() {
        return writeConcern != null ? writeConcern : mongo.getWriteConcern();
    }

    /**
     * Starts a new "consistent request". Following this call and until requestDone() is called, all db operations
     * should use the same underlying connection. This is useful to ensure that operations happen in a certain order
     * with predictable results.
     */
    @Override
    public void requestStart() {
        mongo.requestStart();
    }

    /**
     * Ends the current "consistent request"
     */
    @Override
    public void requestDone() {
        mongo.requestDone();
    }

    /**
     * ensure that a connection is assigned to the current "consistent request" (from primary pool, if connected to a
     * replica set)
     */
    @Override
    public void requestEnsureConnection() {
        requestStart();
    }

    @Override
    public DBCollection getCollection(final String name) {
        DBCollection collection = collectionCache.get(name);
        if (collection != null) {
            return collection;
        }

        collection = new DBCollection(name, this, documentCodec);
        final DBCollection old = collectionCache.putIfAbsent(name, collection);
        return old != null ? old : collection;
    }

    /**
     * Drops this database. Removes all data on disk. Use with caution.
     *
     * @throws MongoException
     */
    @Override
    public void dropDatabase() {
        executeCommand(new DropDatabase());
    }

    /**
     * Returns a collection matching a given string.
     *
     * @param s the name of the collection
     * @return the collection
     */
    @Override
    public DBCollection getCollectionFromString(final String s) {
        return getCollection(s);
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Returns a set containing the names of all collections in this database.
     *
     * @return the names of collections in this database
     * @throws MongoException
     */
    @Override
    public Set<String> getCollectionNames() {
        final MongoNamespace namespacesCollection = new MongoNamespace(name, "system.namespaces");
        final MongoFind findAll = new MongoFind().readPreference(org.mongodb.ReadPreference.primary());
        final QueryResult<Document> query = getConnector().query(
                namespacesCollection,
                findAll,
                documentCodec,
                documentCodec
        );

        final HashSet<String> collections = new HashSet<String>();
        final int lengthOfDatabaseName = getName().length();
        for (final Document namespace : query.getResults()) {
            final String collectionName = (String) namespace.get("name");
            if (!collectionName.contains("$")) {
                final String collectionNameWithoutDatabasePrefix = collectionName.substring(lengthOfDatabaseName + 1);
                collections.add(collectionNameWithoutDatabasePrefix);
            }
        }
        return collections;
    }

    @Override
    public DBCollection createCollection(final String collectionName, final DBObject options) {
        final CreateCollectionOptions createCollectionOptions = toCreateCollectionOptions(collectionName, options);
        try {
            executeCommand(new Create(createCollectionOptions));
            return getCollection(collectionName);
        } catch (MongoCommandFailureException ex) {
            throw new MongoException(ex);
        }

    }

    private CreateCollectionOptions toCreateCollectionOptions(final String collectionName, final DBObject options) {
        if (options.get("size") != null && !(options.get("size") instanceof Integer)) {
            throw new IllegalArgumentException("'size' should be Integer");
        }
        if (options.get("max") != null && !(options.get("max") instanceof Integer)) {
            throw new IllegalArgumentException("'max' should be Integer");
        }
        if (options.get("capped") != null && !(options.get("capped") instanceof Boolean)) {
            throw new IllegalArgumentException("'capped' should be Boolean");
        }
        if (options.get("autoIndexId") != null && !(options.get("capped") instanceof Boolean)) {
            throw new IllegalArgumentException("'capped' should be Boolean");
        }

        boolean capped = false;
        boolean autoIndex = true;
        int sizeInBytes = 0;
        int maxDocuments = 0;
        if (options.get("capped") != null) {
            capped = (Boolean) options.get("capped");
        }
        if (options.get("size") != null) {
            sizeInBytes = (Integer) options.get("size");
        }
        if (options.get("autoIndexId") != null) {
            autoIndex = (Boolean) options.get("autoIndexId");
        }
        if (options.get("max") != null) {
            maxDocuments = (Integer) options.get("max");
        }
        return new CreateCollectionOptions(collectionName, capped, sizeInBytes, autoIndex, maxDocuments);
    }

    /**
     * Executes a database command. This method constructs a simple DBObject using cmd as the field name and {@code
     * true} as its valu, and calls {@link DB#command(com.mongodb.DBObject) }
     *
     * @param cmd command to execute
     * @return result of command from the database
     * @throws MongoException
     * @mongodb.driver.manual tutorial/use-database-commands commands
     */
    public CommandResult command(final String cmd) {
        return command(new BasicDBObject(cmd, Boolean.TRUE), 0, getReadPreference());
    }

    @Override
    public CommandResult command(final String cmd, final int options) {
        return command(new BasicDBObject(cmd, Boolean.TRUE), options, getReadPreference());
    }

    /**
     * Executes a database command.
     *
     * @param cmd document representing the command to execute
     * @return result of command from the database
     * @throws MongoException
     * @mongodb.driver.manual tutorial/use-database-commands commands
     */
    public CommandResult command(final DBObject cmd) {
        return command(cmd, 0, getReadPreference());
    }

    @Override
    public CommandResult command(final DBObject cmd, final int options) {
        return command(cmd, options, getReadPreference());
    }

    /**
     * Executes a database command.
     *
     * @param cmd       dbobject representing the command to execute
     * @param options   query options to use
     * @param readPrefs ReadPreferences for this command (nodes selection is the biggest part of this)
     * @return result of command from the database
     * @throws MongoException
     * @mongodb.driver.manual tutorial/use-database-commands commands
     */
    public CommandResult command(final DBObject cmd, final int options, final ReadPreference readPrefs) {
        //TODO options is not used.
        final MongoCommand mongoCommand = new MongoCommand(toDocument(cmd));
        mongoCommand.readPreference(readPrefs.toNew());
        return new CommandResult(executeCommand(mongoCommand));
    }

    @Override
    public CommandResult command(final DBObject cmd, final DBEncoder encoder) {
        return command(cmd, 0, getReadPreference(), encoder);
    }

    @Override
    public CommandResult command(final DBObject cmd, final int options, final DBEncoder encoder) {
        return command(cmd, options, getReadPreference(), encoder);
    }

    @Override
    public CommandResult command(final DBObject cmd, final int options, final ReadPreference readPrefs,
                                 final DBEncoder encoder) {
        //TODO options is not used.
        final Document document = toDocument(cmd, encoder, documentCodec);
        final MongoCommand mongoCommand = new MongoCommand(document);
        mongoCommand.readPreference(readPrefs.toNew());
        return new CommandResult(executeCommand(mongoCommand));
    }

    /**
     * Gets another database on same server
     *
     * @param name name of the database
     * @return
     */
    public DB getSisterDB(final String name) {
        return mongo.getDB(name);
    }

    @Override
    public boolean collectionExists(final String collectionName) {
        final Set<String> collectionNames = getCollectionNames();
        for (final String name : collectionNames) {
            if (name.equalsIgnoreCase(collectionName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CommandResult getLastError(final WriteConcern concern) {
        final GetLastError getLastErrorCommand = new GetLastError(concern.toNew());
        org.mongodb.result.CommandResult commandResult = executeCommand(getLastErrorCommand);
        return new CommandResult(commandResult);
    }

    @Override
    public CommandResult getLastError() {
        return getLastError(WriteConcern.ACKNOWLEDGED);
    }

    @Override
    public CommandResult getLastError(final int w, final int wtimeout, final boolean fsync) {
        return getLastError(new WriteConcern(w, wtimeout, fsync));
    }

    @Override
    public CommandResult doEval(final String code, final Object... args) {
        final MongoCommand mongoCommand = new MongoCommand(new Document("$eval", code).append("args", args));
        return new CommandResult(executeCommand(mongoCommand));
    }

    @Override
    public Object eval(final String code, final Object... args) {
        final CommandResult result = doEval(code, args);
        result.throwOnError();
        return result.get("retval");
    }

    @Override
    public CommandResult getStats() {
        final MongoCommand mongoCommand = new MongoCommand(new Document("dbStats", 1).append("scale", 1));
        return new CommandResult(executeCommand(mongoCommand));
    }

    @Override
    public CommandResult getPreviousError() {
        final MongoCommand mongoCommand = new MongoCommand(new Document("getPrevError", 1));
        return new CommandResult(executeCommand(mongoCommand));
    }

    @Override
    public void resetError() {
        final MongoCommand mongoCommand = new MongoCommand(new Document("resetError", 1));
        executeCommand(mongoCommand);
    }

    @Override
    public void forceError() {
        final MongoCommand mongoCommand = new MongoCommand(new Document("forceError", 1));
        executeCommand(mongoCommand);
    }

    @Override
    public void cleanCursors(final boolean force) {
        //TODO is it needs to be implemented? Needs to check other drivers behavior.
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    @Override
    public void setReadOnly(final Boolean b) {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    @Override
    public boolean authenticate(final String username, final char[] password) {
        return false;  // TODO: Implement authentication!!!!
    }

    @Override
    public boolean isAuthenticated() {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    @Override
    public CommandResult authenticateCommand(final String username, final char[] password) {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    @Override
    public WriteResult addUser(final String username, final char[] passwd) {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    @Override
    public WriteResult addUser(final String username, final char[] passwd, final boolean readOnly) {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    @Override
    public WriteResult removeUser(final String username) {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    @Override
    @Deprecated
    public void slaveOk() {
        addOption(Bytes.QUERYOPTION_SLAVEOK);
    }

    @Override
    public void addOption(final int option) {
        optionHolder.add(option);
    }

    @Override
    public void setOptions(final int options) {
        optionHolder.set(options);
    }

    @Override
    public void resetOptions() {
        optionHolder.reset();
    }

    @Override
    public int getOptions() {
        return optionHolder.get();
    }

    MongoConnector getConnector() {
        return getMongo().getConnector();
    }

    org.mongodb.result.CommandResult executeCommand(final MongoCommand commandOperation) {
        commandOperation.readPreferenceIfAbsent(getReadPreference().toNew());
        return getConnector().command(getName(), commandOperation, documentCodec);
    }


    Bytes.OptionHolder getOptionHolder() {
        return optionHolder;
    }
}
