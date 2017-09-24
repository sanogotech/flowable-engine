/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flowable.engine.impl.cmd;

import org.flowable.engine.common.api.FlowableIllegalArgumentException;
import org.flowable.engine.common.api.FlowableObjectNotFoundException;
import org.flowable.engine.common.impl.interceptor.Command;
import org.flowable.engine.common.impl.interceptor.CommandContext;
import org.flowable.engine.delegate.event.FlowableEngineEventType;
import org.flowable.engine.delegate.event.impl.FlowableEventBuilder;
import org.flowable.engine.impl.persistence.entity.DeadLetterJobEntity;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.runtime.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * @author Tijs Rademakers
 */

public class DeleteDeadLetterJobCmd implements Command<Object>, Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteDeadLetterJobCmd.class);
    private static final long serialVersionUID = 1L;

    protected String timerJobId;

    public DeleteDeadLetterJobCmd(String timerJobId) {
        this.timerJobId = timerJobId;
    }

    public Object execute(CommandContext commandContext) {
        DeadLetterJobEntity jobToDelete = getJobToDelete(commandContext);

        sendCancelEvent(jobToDelete);
        sendCancelTransactionEvent(jobToDelete);

        CommandContextUtil.getDeadLetterJobEntityManager(commandContext).delete(jobToDelete);
        return null;
    }

    protected void sendCancelEvent(DeadLetterJobEntity jobToDelete) {
        if (CommandContextUtil.getProcessEngineConfiguration().getEventDispatcher().isEnabled()) {
            CommandContextUtil.getProcessEngineConfiguration().getEventDispatcher().dispatchEvent(FlowableEventBuilder.createEntityEvent(FlowableEngineEventType.JOB_CANCELED, jobToDelete));
        }
    }

    protected void sendCancelTransactionEvent(DeadLetterJobEntity jobToDelete) {
        if (CommandContextUtil.getProcessEngineConfiguration().getTransactionDependentEventDispatcher().isEnabled()) {
            CommandContextUtil.getProcessEngineConfiguration().getTransactionDependentEventDispatcher().dispatchEvent(FlowableEventBuilder.createEntityEvent(FlowableEngineEventType.JOB_CANCELED, jobToDelete));
        }
    }

    protected DeadLetterJobEntity getJobToDelete(CommandContext commandContext) {
        if (timerJobId == null) {
            throw new FlowableIllegalArgumentException("jobId is null");
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Deleting job {}", timerJobId);
        }

        DeadLetterJobEntity job = CommandContextUtil.getDeadLetterJobEntityManager(commandContext).findById(timerJobId);
        if (job == null) {
            throw new FlowableObjectNotFoundException("No dead letter job found with id '" + timerJobId + "'", Job.class);
        }

        return job;
    }

}
