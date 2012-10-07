/*
 * Copyright (C) 2012, IBM Corporation and others.
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
package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.merge.MergeStrategy;
import org.junit.Before;
import org.junit.Test;

public class MergeTest extends CLIRepositoryTestCase {

	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		git.commit().setMessage("initial commit").call();
	}

	@Test
	public void testMergeSelf() throws Exception {
		assertEquals("Already up-to-date.", execute("git merge master")[0]);
	}

	@Test
	public void testSquashSelf() throws Exception {
		assertEquals(" (nothing to squash)Already up-to-date.",
				execute("git merge master --squash")[0]);
	}

	@Test
	public void testFastForward() throws Exception {
		git.branchCreate().setName("side").call();
		writeTrashFile("file", "master");
		git.add().addFilepattern("file").call();
		git.commit().setMessage("commit").call();
		git.checkout().setName("side").call();

		assertEquals("Fast-forward", execute("git merge master")[0]);
	}

	@Test
	public void testMerge() throws Exception {
		git.branchCreate().setName("side").call();
		writeTrashFile("master", "content");
		git.add().addFilepattern("master").call();
		git.commit().setMessage("master commit").call();
		git.checkout().setName("side").call();
		writeTrashFile("side", "content");
		git.add().addFilepattern("side").call();
		git.commit().setMessage("side commit").call();

		assertEquals("Merge made by the '" + MergeStrategy.RESOLVE.getName()
				+ "' strategy.", execute("git merge master")[0]);
	}

	@Test
	public void testSquash() throws Exception {
		git.branchCreate().setName("side").call();
		writeTrashFile("file1", "content1");
		git.add().addFilepattern("file1").call();
		git.commit().setMessage("file1 commit").call();
		writeTrashFile("file2", "content2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("file2 commit").call();
		git.checkout().setName("side").call();
		writeTrashFile("side", "content");
		git.add().addFilepattern("side").call();
		git.commit().setMessage("side commit").call();

		assertArrayEquals(
				new String[] { "Squash commit -- not updating HEAD",
						"Automatic merge went well; stopped before committing as requested",
						"" },
				execute("git merge master --squash"));
	}
}
