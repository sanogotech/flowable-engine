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

import org.flowable.engine.common.api.FlowableException;
import org.flowable.engine.common.api.FlowableObjectNotFoundException;
import org.flowable.engine.common.impl.interceptor.Command;
import org.flowable.engine.common.impl.interceptor.CommandContext;
import org.flowable.engine.common.impl.util.IoUtil;
import org.flowable.engine.compatibility.Flowable5CompatibilityHandler;
import org.flowable.engine.delegate.event.FlowableEngineEventType;
import org.flowable.engine.delegate.event.impl.FlowableEventBuilder;
import org.flowable.engine.impl.identity.Authentication;
import org.flowable.engine.impl.persistence.entity.AttachmentEntity;
import org.flowable.engine.impl.persistence.entity.ByteArrayEntity;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.persistence.entity.TaskEntity;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.Flowable5Util;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.task.Attachment;
import org.flowable.engine.task.Task;

import java.io.InputStream;

/**
 * @author Tom Baeyens
 * @author Joram Barrez
 */
// Not Serializable
public class CreateAttachmentCmd implements Command<Attachment> {

    protected String attachmentType;
    protected String taskId;
    protected String processInstanceId;
    protected String attachmentName;
    protected String attachmentDescription;
    protected InputStream content;
    protected String url;

    public CreateAttachmentCmd(String attachmentType, String taskId, String processInstanceId, String attachmentName, String attachmentDescription, InputStream content, String url) {
        this.attachmentType = attachmentType;
        this.taskId = taskId;
        this.processInstanceId = processInstanceId;
        this.attachmentName = attachmentName;
        this.attachmentDescription = attachmentDescription;
        this.content = content;
        this.url = url;
    }

    public Attachment execute(CommandContext commandContext) {

        if (taskId != null) {
            TaskEntity task = verifyTaskParameters(commandContext);
            if (task.getProcessDefinitionId() != null && Flowable5Util.isFlowable5ProcessDefinitionId(commandContext, task.getProcessDefinitionId())) {
                Flowable5CompatibilityHandler compatibilityHandler = Flowable5Util.getFlowable5CompatibilityHandler();
                return compatibilityHandler.createAttachment(attachmentType, taskId, processInstanceId, attachmentName, attachmentDescription, content, url);
            }
        }

        if (processInstanceId != null) {
            ExecutionEntity execution = verifyExecutionParameters(commandContext);
            if (Flowable5Util.isFlowable5ProcessDefinitionId(commandContext, execution.getProcessDefinitionId())) {
                Flowable5CompatibilityHandler compatibilityHandler = Flowable5Util.getFlowable5CompatibilityHandler();
                return compatibilityHandler.createAttachment(attachmentType, taskId, processInstanceId, attachmentName, attachmentDescription, content, url);
            }
        }

        AttachmentEntity attachment = CommandContextUtil.getAttachmentEntityManager().create();
        attachment.setName(attachmentName);
        attachment.setProcessInstanceId(processInstanceId);
        attachment.setTaskId(taskId);
        attachment.setDescription(attachmentDescription);
        attachment.setType(attachmentType);
        attachment.setUrl(url);
        attachment.setUserId(Authentication.getAuthenticatedUserId());
        attachment.setTime(CommandContextUtil.getProcessEngineConfiguration(commandContext).getClock().getCurrentTime());

        CommandContextUtil.getAttachmentEntityManager().insert(attachment, false);

        if (content != null) {
            byte[] bytes = IoUtil.readInputStream(content, attachmentName);
            ByteArrayEntity byteArray = CommandContextUtil.getByteArrayEntityManager().create();
            byteArray.setBytes(bytes);
            CommandContextUtil.getByteArrayEntityManager().insert(byteArray);
            attachment.setContentId(byteArray.getId());
            attachment.setContent(byteArray);
        }

        CommandContextUtil.getHistoryManager(commandContext).createAttachmentComment(taskId, processInstanceId, attachmentName, true);

        dispatchEvent(commandContext, attachment);
        dispatchTransactionEvent(commandContext, attachment);

        return attachment;
    }

    private void dispatchEvent(CommandContext commandContext, AttachmentEntity attachment) {
        if (CommandContextUtil.getProcessEngineConfiguration(commandContext).getEventDispatcher().isEnabled()) {
            // Forced to fetch the process-instance to associate the right
            // process definition
            String processDefinitionId = null;
            if (attachment.getProcessInstanceId() != null) {
                ExecutionEntity process = CommandContextUtil.getExecutionEntityManager(commandContext).findById(processInstanceId);
                if (process != null) {
                    processDefinitionId = process.getProcessDefinitionId();
                }
            }

            CommandContextUtil.getProcessEngineConfiguration(commandContext).getEventDispatcher()
                    .dispatchEvent(FlowableEventBuilder.createEntityEvent(FlowableEngineEventType.ENTITY_CREATED, attachment, processInstanceId, processInstanceId, processDefinitionId));
            CommandContextUtil.getProcessEngineConfiguration(commandContext).getEventDispatcher()
                    .dispatchEvent(FlowableEventBuilder.createEntityEvent(FlowableEngineEventType.ENTITY_INITIALIZED, attachment, processInstanceId, processInstanceId, processDefinitionId));
        }
    }

    private void dispatchTransactionEvent(CommandContext commandContext, AttachmentEntity attachment) {
        if (CommandContextUtil.getProcessEngineConfiguration(commandContext).getTransactionDependentEventDispatcher().isEnabled()) {
            // Forced to fetch the process-instance to associate the right
            // process definition
            String processDefinitionId = null;
            if (attachment.getProcessInstanceId() != null) {
                ExecutionEntity process = CommandContextUtil.getExecutionEntityManager(commandContext).findById(processInstanceId);
                if (process != null) {
                    processDefinitionId = process.getProcessDefinitionId();
                }
            }

            CommandContextUtil.getProcessEngineConfiguration(commandContext).getTransactionDependentEventDispatcher()
                    .dispatchEvent(FlowableEventBuilder.createEntityEvent(FlowableEngineEventType.ENTITY_CREATED, attachment, processInstanceId, processInstanceId, processDefinitionId));
            CommandContextUtil.getProcessEngineConfiguration(commandContext).getTransactionDependentEventDispatcher()
                    .dispatchEvent(FlowableEventBuilder.createEntityEvent(FlowableEngineEventType.ENTITY_INITIALIZED, attachment, processInstanceId, processInstanceId, processDefinitionId));
        }
    }

    protected TaskEntity verifyTaskParameters(CommandContext commandContext) {
        TaskEntity task = CommandContextUtil.getTaskEntityManager(commandContext).findById(taskId);

        if (task == null) {
            throw new FlowableObjectNotFoundException("Cannot find task with id " + taskId, Task.class);
        }

        if (task.isSuspended()) {
            throw new FlowableException("It is not allowed to add an attachment to a suspended task");
        }

        return task;
    }

    protected ExecutionEntity verifyExecutionParameters(CommandContext commandContext) {
        ExecutionEntity execution = CommandContextUtil.getExecutionEntityManager(commandContext).findById(processInstanceId);

        if (execution == null) {
            throw new FlowableObjectNotFoundException("Process instance " + processInstanceId + " doesn't exist", ProcessInstance.class);
        }

        if (execution.isSuspended()) {
            throw new FlowableException("It is not allowed to add an attachment to a suspended process instance");
        }

        return execution;
    }

}
