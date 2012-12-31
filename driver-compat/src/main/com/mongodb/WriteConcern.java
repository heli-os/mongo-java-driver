// WriteConcern.java

/**
 *      Copyright (C) 2008-2011 10gen Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.mongodb;

import org.bson.util.annotations.Immutable;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>WriteConcern control the acknowledgment of write operations with various options. <p> <b>w</b> <ul> <li>-1 = Don't
 * even report network errors </li> <li> 0 = Don't wait for acknowledgement from the server </li> <li> 1 = Wait for
 * acknowledgement, but don't wait for secondaries to replicate</li> <li> 2+= Wait for one or more secondaries to also
 * acknowledge </li> </ul> <b>wtimeout</b> how long to wait for slaves before failing <ul> <li>0: indefinite </li>
 * <li>greater than 0: ms to wait </li> </ul> </p>
 * <p/>
 * Other options: <ul> <li><b>j</b>: wait for group commit to journal</li> <li><b>fsync</b>: force fsync to disk</li>
 * </ul>
 *
 * @dochub databases
 */
@Immutable
public class WriteConcern implements Serializable {

    private static final long serialVersionUID = 1884671104750417011L;

    // map of the constants from above for use by fromString
    private static final Map<String, WriteConcern> _namedConcerns;

    private final org.mongodb.WriteConcern proxied;

    /**
     * No exceptions are raised, even for network issues.
     */
    public final static WriteConcern ERRORS_IGNORED = new WriteConcern(org.mongodb.WriteConcern.ERRORS_IGNORED);

    /**
     * Write operations that use this write concern will wait for acknowledgement from the primary server before
     * returning. Exceptions are raised for network issues, and server errors.
     *
     * @since 2.10.0
     */
    public final static WriteConcern ACKNOWLEDGED = new WriteConcern(org.mongodb.WriteConcern.ACKNOWLEDGED);
    /**
     * Write operations that use this write concern will return as soon as the message is written to the socket.
     * Exceptions are raised for network issues, but not server errors.
     *
     * @since 2.10.0
     */
    public final static WriteConcern UNACKNOWLEDGED = new WriteConcern(org.mongodb.WriteConcern.UNACKNOWLEDGED);

    /**
     * Exceptions are raised for network issues, and server errors; the write operation waits for the server to flush
     * the data to disk.
     */
    public final static WriteConcern FSYNCED = new WriteConcern(org.mongodb.WriteConcern.FSYNCED);

    /**
     * Exceptions are raised for network issues, and server errors; the write operation waits for the server to group
     * commit to the journal file on disk.
     */
    public final static WriteConcern JOURNALED = new WriteConcern(org.mongodb.WriteConcern.JOURNALED);

    /**
     * Exceptions are raised for network issues, and server errors; waits for at least 2 servers for the write
     * operation.
     */
    public final static WriteConcern REPLICA_ACKNOWLEDGED = new WriteConcern(
            org.mongodb.WriteConcern.REPLICA_ACKNOWLEDGED);

    /**
     * No exceptions are raised, even for network issues.
     * <p/>
     * This field has been superseded by {@code WriteConcern.ERRORS_IGNORED}, and may be deprecated in a future
     * release.
     *
     * @see WriteConcern#ERRORS_IGNORED
     */
    public final static WriteConcern NONE = ERRORS_IGNORED;

    /**
     * Write operations that use this write concern will return as soon as the message is written to the socket.
     * Exceptions are raised for network issues, but not server errors.
     * <p/>
     * This field has been superseded by {@code WriteConcern.UNACKNOWLEDGED}, and may be deprecated in a future
     * release.
     *
     * @see WriteConcern#UNACKNOWLEDGED
     */
    public final static WriteConcern NORMAL = UNACKNOWLEDGED;

    /**
     * Write operations that use this write concern will wait for acknowledgement from the primary server before
     * returning. Exceptions are raised for network issues, and server errors.
     * <p/>
     * This field has been superseded by {@code WriteConcern.ACKNOWLEDGED}, and may be deprecated in a future release.
     *
     * @see WriteConcern#ACKNOWLEDGED
     */
    public final static WriteConcern SAFE = ACKNOWLEDGED;

    /**
     * Exceptions are raised for network issues, and server errors; waits on a majority of servers for the write
     * operation.
     */
    public final static WriteConcern MAJORITY = new WriteConcern("majority");

    /**
     * Exceptions are raised for network issues, and server errors; the write operation waits for the server to flush
     * the data to disk.
     * <p/>
     * This field has been superseded by {@code WriteConcern.FSYNCED}, and may be deprecated in a future release.
     *
     * @see WriteConcern#FSYNCED
     */
    public final static WriteConcern FSYNC_SAFE = FSYNCED;

    /**
     * Exceptions are raised for network issues, and server errors; the write operation waits for the server to group
     * commit to the journal file on disk.
     * <p/>
     * This field has been superseded by {@code WriteConcern.JOURNALED}, and may be deprecated in a future release.
     *
     * @see WriteConcern#JOURNALED
     */
    public final static WriteConcern JOURNAL_SAFE = JOURNALED;

    /**
     * Exceptions are raised for network issues, and server errors; waits for at least 2 servers for the write
     * operation.
     * <p/>
     * This field has been superseded by {@code WriteConcern.REPLICA_ACKNOWLEDGED}, and may be deprecated in a future
     * release.
     *
     * @see WriteConcern#REPLICA_ACKNOWLEDGED
     */
    public final static WriteConcern REPLICAS_SAFE = REPLICA_ACKNOWLEDGED;

    /**
     * Default constructor keeping all options as default.  Be careful using this constructor, as it's equivalent to
     * {@code WriteConcern.UNACKNOWLEDGED}, so writes may be lost without any errors being reported.
     *
     * @see WriteConcern#UNACKNOWLEDGED
     */
    public WriteConcern() {
        this(0);
    }

    /**
     * Calls {@link WriteConcern#WriteConcern(int, int, boolean)} with wtimeout=0 and fsync=false
     *
     * @param w number of writes
     */
    public WriteConcern(int w) {
        this(w, 0, false);
    }

    /**
     * Tag based Write Concern with wtimeout=0, fsync=false, and j=false
     *
     * @param w Write Concern tag
     */
    public WriteConcern(String w) {
        this(w, 0, false, false);
    }

    /**
     * Calls {@link WriteConcern#WriteConcern(int, int, boolean)} with fsync=false
     *
     * @param w        number of writes
     * @param wtimeout timeout for write operation
     */
    public WriteConcern(int w, int wtimeout) {
        this(w, wtimeout, false);
    }

    /**
     * Calls {@link WriteConcern#WriteConcern(int, int, boolean)} with w=1 and wtimeout=0
     *
     * @param fsync whether or not to fsync
     */
    public WriteConcern(boolean fsync) {
        this(1, 0, fsync);
    }

    /**
     * Creates a WriteConcern object. <p>Specifies the number of servers to wait for on the write operation, and
     * exception raising behavior </p> <p> w represents the number of servers: <ul> <li>{@code w=-1} None, no checking
     * is done</li> <li>{@code w=0} None, network socket errors raised</li> <li>{@code w=1} Checks server for errors as
     * well as network socket errors raised</li> <li>{@code w>1} Checks servers (w) for errors as well as network socket
     * errors raised</li> </ul> </p>
     *
     * @param w        number of writes
     * @param wtimeout timeout for write operation
     * @param fsync    whether or not to fsync
     */
    public WriteConcern(int w, int wtimeout, boolean fsync) {
        this(w, wtimeout, fsync, false);
    }

    /**
     * Creates a WriteConcern object. <p>Specifies the number of servers to wait for on the write operation, and
     * exception raising behavior </p> <p> w represents the number of servers: <ul> <li>{@code w=-1} None, no checking
     * is done</li> <li>{@code w=0} None, network socket errors raised</li> <li>{@code w=1} Checks server for errors as
     * well as network socket errors raised</li> <li>{@code w>1} Checks servers (w) for errors as well as network socket
     * errors raised</li> </ul> </p>
     *
     * @param w        number of writes
     * @param wtimeout timeout for write operation
     * @param fsync    whether or not to fsync
     * @param j        whether writes should wait for a journaling group commit
     */
    public WriteConcern(int w, int wtimeout, boolean fsync, boolean j) {
        this(w, wtimeout, fsync, j, false);
    }

    /**
     * Creates a WriteConcern object. <p>Specifies the number of servers to wait for on the write operation, and
     * exception raising behavior </p> <p> w represents the number of servers: <ul> <li>{@code w=-1} None, no checking
     * is done</li> <li>{@code w=0} None, network socket errors raised</li> <li>{@code w=1} Checks server for errors as
     * well as network socket errors raised</li> <li>{@code w>1} Checks servers (w) for errors as well as network socket
     * errors raised</li> </ul> </p>
     *
     * @param w                     number of writes
     * @param wtimeout              timeout for write operation
     * @param fsync                 whether or not to fsync
     * @param j                     whether writes should wait for a journaling group commit
     * @param continueOnInsertError if batch inserts should continue after the first error
     */
    public WriteConcern(int w, int wtimeout, boolean fsync, boolean j, boolean continueOnInsertError) {
        proxied = new org.mongodb.WriteConcern(w, wtimeout, fsync, j, continueOnInsertError);
    }

    /**
     * Creates a WriteConcern object. <p>Specifies the number of servers to wait for on the write operation, and
     * exception raising behavior </p> <p> w represents the number of servers: <ul> <li>{@code w=-1} None, no checking
     * is done</li> <li>{@code w=0} None, network socket errors raised</li> <li>{@code w=1} Checks server for errors as
     * well as network socket errors raised</li> <li>{@code w>1} Checks servers (w) for errors as well as network socket
     * errors raised</li> </ul> </p>
     *
     * @param w        number of writes
     * @param wtimeout timeout for write operation
     * @param fsync    whether or not to fsync
     * @param j        whether writes should wait for a journaling group commit
     */
    public WriteConcern(String w, int wtimeout, boolean fsync, boolean j) {
        this(w, wtimeout, fsync, j, false);
    }

    /**
     * Creates a WriteConcern object. <p>Specifies the number of servers to wait for on the write operation, and
     * exception raising behavior </p> <p> w represents the number of servers: <ul> <li>{@code w=-1} None, no checking
     * is done</li> <li>{@code w=0} None, network socket errors raised</li> <li>{@code w=1} Checks server for errors as
     * well as network socket errors raised</li> <li>{@code w>1} Checks servers (w) for errors as well as network socket
     * errors raised</li> </ul> </p>
     *
     * @param w                     number of writes
     * @param wtimeout              timeout for write operation
     * @param fsync                 whether or not to fsync
     * @param j                     whether writes should wait for a journaling group commit
     * @param continueOnInsertError if batch inserts should continue after the first error
     */
    public WriteConcern(String w, int wtimeout, boolean fsync, boolean j, boolean continueOnInsertError) {
        proxied = new org.mongodb.WriteConcern(w, wtimeout, fsync, j, continueOnInsertError);
    }

    /**
     * Creates a WriteConcern based on an instance of org.mongodb.WriteConcern.
     *
     * @param writeConcern the write concern to copy
     */
    public WriteConcern(final org.mongodb.WriteConcern writeConcern) {
        proxied = writeConcern;
    }

    /**
     * Gets the getlasterror command for this write concern.
     *
     * @return getlasterror command, even if <code>w <= 0</code>
     */
    public BasicDBObject getCommand() {
        return DBObjects.toDBObject(proxied.getCommand());
    }

    /**
     * Gets the w value (the write strategy)
     *
     * @return w, either an instance of Integer or String
     */
    public Object getWObject() {
        return proxied.getWObject();
    }

    /**
     * Gets the w parameter (the write strategy)
     *
     * @return w, as an int
     * @throws ClassCastException if w is not an integer
     */
    public int getW() {
        return proxied.getW();
    }

    /**
     * Gets the w parameter (the write strategy) in String format
     *
     * @return w as a string
     * @throws ClassCastException if w is not a String
     */
    public String getWString() {
        return proxied.getWString();
    }

    /**
     * Gets the write timeout (in milliseconds)
     *
     * @return the timeout
     */
    public int getWtimeout() {
        return proxied.getWtimeout();
    }

    /**
     * Gets the fsync flag (fsync to disk on the server)
     *
     * @return the fsync flag
     */
    public boolean getFsync() {
        return proxied.getFsync();
    }

    /**
     * Gets the fsync flag (fsync to disk on the server)
     *
     * @return the fsync flag
     */
    public boolean fsync() {
        return proxied.getFsync();
    }

    /**
     * Returns whether network error may be raised (w >= 0)
     *
     * @return whether an exception will be thrown for IOException from the underlying socket
     */
    public boolean raiseNetworkErrors() {
        return proxied.raiseNetworkErrors();
    }

    /**
     * Returns whether "getlasterror" should be called (w > 0)
     *
     * @return whether this write concern will result in an an acknowledged write
     */
    public boolean callGetLastError() {
        return proxied.callGetLastError();
    }

    /**
     * Gets the WriteConcern constants by name (matching is done case insensitively).
     *
     * @param name
     * @return
     */
    public static WriteConcern valueOf(String name) {
        return _namedConcerns.get(name.toLowerCase());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final WriteConcern that = (WriteConcern) o;

        if (!proxied.equals(that.proxied)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return proxied.hashCode();
    }

    @Override
    public String toString() {
        return proxied.toString();
    }

    /**
     * Gets the j parameter (journal syncing)
     *
     * @return
     */
    public boolean getJ() {
        return proxied.getJ();
    }

    /**
     * Gets the "continue inserts on error" mode
     *
     * @return
     */
    public boolean getContinueOnErrorForInsert() {
        return proxied.getContinueOnErrorForInsert();
    }

    public org.mongodb.WriteConcern toNew() {
        return proxied;
    }

    /**
     * Toggles the "continue inserts on error" mode. This only applies to server side errors. If there is a document
     * which does not validate in the client, an exception will still be thrown in the client. This will return a new
     * WriteConcern instance with the specified continueOnInsert value.
     *
     * @param continueOnErrorForInsert
     */
    public WriteConcern continueOnErrorForInsert(boolean continueOnErrorForInsert) {
        return new WriteConcern(proxied.withContinueOnErrorForInsert(continueOnErrorForInsert));
    }

    /**
     * Create a Majority Write Concern that requires a majority of servers to acknowledge the write.
     *
     * @param wtimeout timeout for write operation
     * @param fsync    whether or not to fsync
     * @param j        whether writes should wait for a journal group commit
     */
    public static Majority majorityWriteConcern(int wtimeout, boolean fsync, boolean j) {
        return new Majority(wtimeout, fsync, j);
    }

    public static class Majority extends WriteConcern {

        private static final long serialVersionUID = -4128295115883875212L;

        public Majority() {
            this(0, false, false);
        }

        public Majority(int wtimeout, boolean fsync, boolean j) {
            super("majority", wtimeout, fsync, j);
        }
    }

    static {
        _namedConcerns = new HashMap<String, WriteConcern>();
        for (Field f : WriteConcern.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType().equals(WriteConcern.class)) {
                String key = f.getName().toLowerCase();
                try {
                    _namedConcerns.put(key, (WriteConcern) f.get(null));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
