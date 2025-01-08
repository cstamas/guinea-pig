/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.mimir.shared.impl;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.mimir.shared.CacheEntry;
import eu.maveniverse.maven.mimir.shared.CacheKey;
import eu.maveniverse.maven.mimir.shared.Session;
import eu.maveniverse.maven.mimir.shared.naming.NameMapper;
import eu.maveniverse.maven.mimir.shared.node.LocalNode;
import eu.maveniverse.maven.mimir.shared.node.Node;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionImpl implements Session {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final NameMapper nameMapper;
    private final LocalNode localNode;
    private final List<Node> nodes;
    private final Stats stats;

    public SessionImpl(NameMapper nameMapper, LocalNode localNode, List<Node> nodes) {
        this.nameMapper = requireNonNull(nameMapper, "nameMapper");
        this.localNode = requireNonNull(localNode, "localNode");
        this.nodes = requireNonNull(nodes, "nodes");
        this.stats = new Stats();
    }

    @Override
    public boolean supports(RemoteRepository repository) {
        return nameMapper.supports(repository);
    }

    @Override
    public Optional<CacheKey> cacheKey(RemoteRepository remoteRepository, Artifact artifact) {
        return nameMapper.cacheKey(remoteRepository, artifact);
    }

    @Override
    public Optional<CacheEntry> locate(CacheKey key) throws IOException {
        requireNonNull(key, "key");
        Optional<CacheEntry> result = localNode.locate(key);
        if (result.isEmpty()) {
            for (Node node : nodes) {
                result = node.locate(key);
                if (result.isPresent()) {
                    result = Optional.of(localNode.store(
                            key, result.orElseThrow(() -> new IllegalStateException("should be present"))));
                    break;
                }
            }
        }
        return stats.query(result);
    }

    @Override
    public void store(CacheKey key, Path content) throws IOException {
        requireNonNull(key, "key");
        requireNonNull(content, "content");
        if (!Files.isRegularFile(content)) {
            throw new IllegalArgumentException("Not a regular file: " + content);
        }
        stats.store(localNode.store(key, content));
    }

    @Override
    public void close() {
        ArrayList<Exception> exceptions = new ArrayList<>();
        for (Node node : this.nodes) {
            try {
                node.close();
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        try {
            localNode.close();
        } catch (Exception e) {
            exceptions.add(e);
        }
        if (!exceptions.isEmpty()) {
            IllegalStateException illegalStateException = new IllegalStateException("Could not close session");
            exceptions.forEach(illegalStateException::addSuppressed);
            throw illegalStateException;
        }
        logger.info(
                "Mimir session closed (LOCATE/HIT={}/{} STORED={})",
                stats.queries(),
                stats.queryHits(),
                stats.stores());
    }
}
