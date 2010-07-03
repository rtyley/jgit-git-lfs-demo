/*
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2009, Johannes E. Schindelin
 * Copyright (C) 2009, Johannes Schindelin <johannes.schindelin@gmx.de>
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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextIgnoreAllWhitespace;
import org.eclipse.jgit.diff.RawTextIgnoreLeadingWhitespace;
import org.eclipse.jgit.diff.RawTextIgnoreTrailingWhitespace;
import org.eclipse.jgit.diff.RawTextIgnoreWhitespaceChange;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.pgm.opt.PathTreeFilterHandler;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_ShowDiffs")
class Diff extends TextBuiltin {
	@Argument(index = 0, metaVar = "metaVar_treeish", required = true)
	void tree_0(final AbstractTreeIterator c) {
		trees.add(c);
	}

	@Argument(index = 1, metaVar = "metaVar_treeish", required = true)
	private final List<AbstractTreeIterator> trees = new ArrayList<AbstractTreeIterator>();

	@Option(name = "--", metaVar = "metaVar_paths", multiValued = true, handler = PathTreeFilterHandler.class)
	private TreeFilter pathFilter = TreeFilter.ALL;

	@Option(name = "-M", usage = "usage_detectRenames")
	private boolean detectRenames;

	@Option(name = "--name-status", usage = "usage_nameStatus")
	private boolean showNameAndStatusOnly;

	@Option(name = "--ignore-space-at-eol")
	private boolean ignoreWsTrailing;

	@Option(name = "--ignore-leading-space")
	private boolean ignoreWsLeading;

	@Option(name = "-b", aliases = { "--ignore-space-change" })
	private boolean ignoreWsChange;

	@Option(name = "-w", aliases = { "--ignore-all-space" })
	private boolean ignoreWsAll;

	@Option(name = "-U", aliases = { "--unified" }, metaVar = "metaVar_linesOfContext")
	void unified(int lines) {
		fmt.setContext(lines);
	}

	private DiffFormatter fmt = new DiffFormatter() {
		@Override
		protected RawText newRawText(byte[] raw) {
			if (ignoreWsAll)
				return new RawTextIgnoreAllWhitespace(raw);
			else if (ignoreWsTrailing)
				return new RawTextIgnoreTrailingWhitespace(raw);
			else if (ignoreWsChange)
				return new RawTextIgnoreWhitespaceChange(raw);
			else if (ignoreWsLeading)
				return new RawTextIgnoreLeadingWhitespace(raw);
			else
				return new RawText(raw);
		}
	};

	@Override
	protected void run() throws Exception {
		List<DiffEntry> files = scan();

		if (showNameAndStatusOnly) {
			nameStatus(out, files);
			out.flush();

		} else {
			BufferedOutputStream o = new BufferedOutputStream(System.out);
			fmt.format(o, db, files);
			o.flush();
		}
	}

	static void nameStatus(PrintWriter out, List<DiffEntry> files) {
		for (DiffEntry ent : files) {
			switch (ent.getChangeType()) {
			case ADD:
				out.println("A\t" + ent.getNewName());
				break;
			case DELETE:
				out.println("D\t" + ent.getOldName());
				break;
			case MODIFY:
				out.println("M\t" + ent.getNewName());
				break;
			case COPY:
				out.format("C%1$03d\t%2$s\t%3$s", ent.getScore(), //
						ent.getOldName(), ent.getNewName());
				out.println();
				break;
			case RENAME:
				out.format("R%1$03d\t%2$s\t%3$s", ent.getScore(), //
						ent.getOldName(), ent.getNewName());
				out.println();
				break;
			}
		}
	}

	private List<DiffEntry> scan() throws IOException {
		final TreeWalk walk = new TreeWalk(db);
		walk.reset();
		walk.setRecursive(true);
		for (final AbstractTreeIterator i : trees)
			walk.addTree(i);
		walk.setFilter(AndTreeFilter.create(TreeFilter.ANY_DIFF, pathFilter));

		List<DiffEntry> files = DiffEntry.scan(walk);
		if (detectRenames) {
			RenameDetector rd = new RenameDetector(db);
			rd.addAll(files);
			files = rd.compute(new TextProgressMonitor());
		}
		return files;
	}
}
