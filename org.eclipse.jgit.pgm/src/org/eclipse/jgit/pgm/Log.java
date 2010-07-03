/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2006-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_viewCommitHistory")
class Log extends RevWalkTextBuiltin {
	private final TimeZone myTZ = TimeZone.getDefault();

	private final DateFormat fmt;

	private Map<AnyObjectId, Set<Ref>> allRefsByPeeledObjectId;

	@Option(name="--decorate", usage="usage_showRefNamesMatchingCommits")
	private boolean decorate;

	@Option(name = "-M", usage = "usage_detectRenames")
	private boolean detectRenames;

	@Option(name = "--name-status", usage = "usage_nameStatus")
	private boolean showNameAndStatusOnly;

	@Option(name = "-p", usage = "usage_showPatch")
	private boolean showPatch;

	@Option(name = "-U", aliases = { "--unified" }, metaVar = "metaVar_linesOfContext")
	void unified(int lines) {
		diffFmt.setContext(lines);
	}

	private DiffFormatter diffFmt = new DiffFormatter();

	Log() {
		fmt = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy ZZZZZ", Locale.US);
	}

	@Override
	protected RevWalk createWalk() {
		RevWalk ret = super.createWalk();
		if (decorate)
			allRefsByPeeledObjectId = getRepository().getAllRefsByPeeledObjectId();
		return ret;
	}

	@Override
	protected void show(final RevCommit c) throws Exception {
		out.print(CLIText.get().commitLabel);
		out.print(" ");
		c.getId().copyTo(outbuffer, out);
		if (decorate) {
			Collection<Ref> list = allRefsByPeeledObjectId.get(c.copy());
			if (list != null) {
				out.print(" (");
				for (Iterator<Ref> i = list.iterator(); i.hasNext(); ) {
					out.print(i.next().getName());
					if (i.hasNext())
						out.print(" ");
				}
				out.print(")");
			}
		}
		out.println();

		final PersonIdent author = c.getAuthorIdent();
		out.println(MessageFormat.format(CLIText.get().authorInfo, author.getName(), author.getEmailAddress()));

		final TimeZone authorTZ = author.getTimeZone();
		fmt.setTimeZone(authorTZ != null ? authorTZ : myTZ);
		out.println(MessageFormat.format(CLIText.get().dateInfo, fmt.format(author.getWhen())));

		out.println();
		final String[] lines = c.getFullMessage().split("\n");
		for (final String s : lines) {
			out.print("    ");
			out.print(s);
			out.println();
		}

		out.println();
		if (c.getParentCount() > 0 && (showNameAndStatusOnly || showPatch))
			showDiff(c);
		out.flush();
	}

	private void showDiff(RevCommit c) throws IOException {
		final TreeWalk tw = new TreeWalk(db);
		tw.reset();
		tw.setRecursive(true);
		tw.addTree(c.getParent(0).getTree());
		tw.addTree(c.getTree());
		tw.setFilter(AndTreeFilter.create(TreeFilter.ANY_DIFF, pathFilter));

		List<DiffEntry> files = DiffEntry.scan(tw);
		if (detectRenames) {
			RenameDetector rd = new RenameDetector(db);
			rd.addAll(files);
			files = rd.compute(new TextProgressMonitor());
		}

		if (showNameAndStatusOnly) {
			Diff.nameStatus(out, files);

		} else {
			out.flush();
			BufferedOutputStream o = new BufferedOutputStream(System.out);
			diffFmt.format(o, db, files);
			o.flush();
		}
	}
}
