/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.cassandra.mail.task;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesService.Context;
import org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesService.Context.Snapshot;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class SolveMessageInconsistenciesTask implements Task {

    static final TaskType SOLVE_MESSAGE_INCONSISTENCIES = TaskType.of("solve-message-inconsistencies");

    public static class Details implements TaskExecutionDetails.AdditionalInformation {
        private final Instant instant;
        private final long processedImapUidEntries;
        private final long processedMessageIdEntries;
        private final long addedMessageIdEntries;
        private final long updatedMessageIdEntries;
        private final long removedMessageIdEntries;
        private final ImmutableList<MessageInconsistenciesEntry> fixedInconsistencies;
        private final ImmutableList<MessageInconsistenciesEntry> errors;

        public Details(Instant instant, long processedImapUidEntries, long processedMessageIdEntries,
                       long addedMessageIdEntries, long updatedMessageIdEntries, long removedMessageIdEntries,
                       ImmutableList<MessageInconsistenciesEntry> fixedInconsistencies, ImmutableList<MessageInconsistenciesEntry> errors) {
            this.instant = instant;
            this.processedImapUidEntries = processedImapUidEntries;
            this.processedMessageIdEntries = processedMessageIdEntries;
            this.addedMessageIdEntries = addedMessageIdEntries;
            this.updatedMessageIdEntries = updatedMessageIdEntries;
            this.removedMessageIdEntries = removedMessageIdEntries;
            this.fixedInconsistencies = fixedInconsistencies;
            this.errors = errors;
        }

        @Override
        public Instant timestamp() {
            return instant;
        }

        @JsonProperty("processedImapUidEntries")
        public long getProcessedImapUidEntries() {
            return processedImapUidEntries;
        }

        @JsonProperty("processedMessageIdEntries")
        public long getProcessedMessageIdEntries() {
            return processedMessageIdEntries;
        }

        @JsonProperty("addedMessageIdEntries")
        public long getAddedMessageIdEntries() {
            return addedMessageIdEntries;
        }

        @JsonProperty("updatedMessageIdEntries")
        public long getUpdatedMessageIdEntries() {
            return updatedMessageIdEntries;
        }

        @JsonProperty("removedMessageIdEntries")
        public long getRemovedMessageIdEntries() {
            return removedMessageIdEntries;
        }

        @JsonProperty("fixedInconsistencies")
        public ImmutableList<MessageInconsistenciesEntry> getFixedInconsistencies() {
            return fixedInconsistencies;
        }

        @JsonProperty("errors")
        public ImmutableList<MessageInconsistenciesEntry> getErrors() {
            return errors;
        }
    }

    private final SolveMessageInconsistenciesService service;
    private Context context;

    public SolveMessageInconsistenciesTask(SolveMessageInconsistenciesService service) {
        this.service = service;
        this.context = new Context();
    }

    @Override
    public Result run() {
        return service.fixMessageInconsistencies(context)
            .block();
    }

    @Override
    public TaskType type() {
        return SOLVE_MESSAGE_INCONSISTENCIES;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        Snapshot snapshot = context.snapshot();
        return Optional.of(new Details(Clock.systemUTC().instant(), snapshot.getProcessedImapUidEntries(), snapshot.getProcessedMessageIdEntries(),
            snapshot.getAddedMessageIdEntries(), snapshot.getUpdatedMessageIdEntries(), snapshot.getRemovedMessageIdEntries(),
            snapshot.getFixedInconsistencies().stream()
                .map(this::toMessageInconsistenciesEntry)
                .collect(Guavate.toImmutableList()),
            snapshot.getErrors().stream()
                .map(this::toMessageInconsistenciesEntry)
                .collect(Guavate.toImmutableList())));
    }

    private MessageInconsistenciesEntry toMessageInconsistenciesEntry(ComposedMessageId composedMessageId) {
        return MessageInconsistenciesEntry.builder()
            .mailboxId(composedMessageId.getMailboxId().serialize())
            .messageId(composedMessageId.getMessageId().serialize())
            .messageUid(composedMessageId.getUid().asLong());
    }
}
