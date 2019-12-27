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
package org.flowable.eventregistry.model;

/**
 * @author Joram Barrez
 */
public class InboundChannelModel extends ChannelModel {

    protected String type;

    protected Object inboundEventChannelAdapter;
    protected Object inboundEventProcessingPipeline;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getInboundEventProcessingPipeline() {
        return inboundEventProcessingPipeline;
    }

    public void setInboundEventProcessingPipeline(Object inboundEventProcessingPipeline) {
        this.inboundEventProcessingPipeline = inboundEventProcessingPipeline;
    }

    public Object getInboundEventChannelAdapter() {
        return inboundEventChannelAdapter;
    }

    public void setInboundEventChannelAdapter(Object inboundEventChannelAdapter) {
        this.inboundEventChannelAdapter = inboundEventChannelAdapter;
    }

}