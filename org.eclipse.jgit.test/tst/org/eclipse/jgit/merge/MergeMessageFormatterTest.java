/*
 * Copyright (C) 2010, Robin Stocker <robin@nibor.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.merge;

import java.io.IOException;
import java.util.Arrays;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.SampleDataRepositoryTestCase;
import org.eclipse.jgit.lib.Ref.Storage;

/**
 * Test construction of merge message by {@link MergeMessageFormatter}.
 */
public class MergeMessageFormatterTest extends SampleDataRepositoryTestCase {

	private MergeMessageFormatter formatter;

	@Override
	protected void setUp() throws Exception {
		super.setUp();

		RefUpdate createRemoteRefA = db
				.updateRef("refs/remotes/origin/remote-a");
		createRemoteRefA.setNewObjectId(db.resolve("refs/heads/a"));
		createRemoteRefA.update();

		RefUpdate createRemoteRefB = db
				.updateRef("refs/remotes/origin/remote-b");
		createRemoteRefB.setNewObjectId(db.resolve("refs/heads/b"));
		createRemoteRefB.update();

		formatter = new MergeMessageFormatter();
	}

	public void testOneBranch() throws IOException {
		Ref a = db.getRef("refs/heads/a");
		Ref master = db.getRef("refs/heads/master");
		String message = formatter.format(Arrays.asList(a), master);
		assertEquals("Merge branch 'a'", message);
	}

	public void testTwoBranches() throws IOException {
		Ref a = db.getRef("refs/heads/a");
		Ref b = db.getRef("refs/heads/b");
		Ref master = db.getRef("refs/heads/master");
		String message = formatter.format(Arrays.asList(a, b), master);
		assertEquals("Merge branches 'a' and 'b'", message);
	}

	public void testThreeBranches() throws IOException {
		Ref c = db.getRef("refs/heads/c");
		Ref b = db.getRef("refs/heads/b");
		Ref a = db.getRef("refs/heads/a");
		Ref master = db.getRef("refs/heads/master");
		String message = formatter.format(Arrays.asList(c, b, a), master);
		assertEquals("Merge branches 'c', 'b' and 'a'", message);
	}

	public void testRemoteBranch() throws Exception {
		Ref remoteA = db.getRef("refs/remotes/origin/remote-a");
		Ref master = db.getRef("refs/heads/master");
		String message = formatter.format(Arrays.asList(remoteA), master);
		assertEquals("Merge remote branch 'origin/remote-a'", message);
	}

	public void testMixed() throws IOException {
		Ref c = db.getRef("refs/heads/c");
		Ref remoteA = db.getRef("refs/remotes/origin/remote-a");
		Ref master = db.getRef("refs/heads/master");
		String message = formatter.format(Arrays.asList(c, remoteA), master);
		assertEquals("Merge branch 'c', remote branch 'origin/remote-a'",
				message);
	}

	public void testTag() throws IOException {
		Ref tagA = db.getRef("refs/tags/A");
		Ref master = db.getRef("refs/heads/master");
		String message = formatter.format(Arrays.asList(tagA), master);
		assertEquals("Merge tag 'A'", message);
	}

	public void testCommit() throws IOException {
		ObjectId objectId = ObjectId
				.fromString("6db9c2ebf75590eef973081736730a9ea169a0c4");
		Ref commit = new ObjectIdRef.Unpeeled(Storage.LOOSE,
				objectId.getName(), objectId);
		Ref master = db.getRef("refs/heads/master");
		String message = formatter.format(Arrays.asList(commit), master);
		assertEquals("Merge commit '6db9c2ebf75590eef973081736730a9ea169a0c4'",
				message);
	}

	public void testPullWithUri() throws IOException {
		String name = "branch 'test' of http://egit.eclipse.org/jgit.git";
		ObjectId objectId = ObjectId
				.fromString("6db9c2ebf75590eef973081736730a9ea169a0c4");
		Ref remoteBranch = new ObjectIdRef.Unpeeled(Storage.LOOSE, name,
				objectId);
		Ref master = db.getRef("refs/heads/master");
		String message = formatter.format(Arrays.asList(remoteBranch), master);
		assertEquals("Merge branch 'test' of http://egit.eclipse.org/jgit.git",
				message);
	}

	public void testIntoOtherThanMaster() throws IOException {
		Ref a = db.getRef("refs/heads/a");
		Ref b = db.getRef("refs/heads/b");
		String message = formatter.format(Arrays.asList(a), b);
		assertEquals("Merge branch 'a' into b", message);
	}
}
