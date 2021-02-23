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
package org.flowable.ui.modeler.service;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.flowable.bpmn.BpmnAutoLayout;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.cmmn.converter.CmmnXmlConverter;
import org.flowable.cmmn.editor.json.converter.CmmnJsonConverter;
import org.flowable.cmmn.model.Case;
import org.flowable.cmmn.model.CmmnModel;
import org.flowable.dmn.editor.converter.DmnJsonConverter;
import org.flowable.dmn.model.DecisionService;
import org.flowable.dmn.model.DmnDefinition;
import org.flowable.dmn.xml.converter.DmnXMLConverter;
import org.flowable.editor.language.json.converter.BpmnJsonConverter;
import org.flowable.editor.language.json.converter.util.CollectionUtils;
import org.flowable.ui.common.model.ResultListDataRepresentation;
import org.flowable.ui.common.security.SecurityUtils;
import org.flowable.ui.common.service.exception.BadRequestException;
import org.flowable.ui.common.service.exception.ConflictingRequestException;
import org.flowable.ui.common.service.exception.InternalServerErrorException;
import org.flowable.ui.common.util.XmlUtil;
import org.flowable.ui.modeler.domain.AbstractModel;
import org.flowable.ui.modeler.domain.AppDefinition;
import org.flowable.ui.modeler.domain.Model;
import org.flowable.ui.modeler.model.AppDefinitionListModelRepresentation;
import org.flowable.ui.modeler.model.ModelRepresentation;
import org.flowable.ui.modeler.repository.ModelRepository;
import org.flowable.ui.modeler.repository.ModelSort;
import org.flowable.ui.modeler.serviceapi.ModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Tijs Rademakers
 */
@Transactional
public class FlowableModelQueryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowableModelQueryService.class);

    protected static final String FILTER_SHARED_WITH_ME = "sharedWithMe";
    protected static final String FILTER_SHARED_WITH_OTHERS = "sharedWithOthers";
    protected static final String FILTER_FAVORITE = "favorite";

    protected static final int MIN_FILTER_LENGTH = 1;

    @Autowired
    protected ModelRepository modelRepository;

    @Autowired
    protected ModelService modelService;

    @Autowired
    protected ObjectMapper objectMapper;

    protected BpmnXMLConverter bpmnXmlConverter = new BpmnXMLConverter();
    protected BpmnJsonConverter bpmnJsonConverter = new BpmnJsonConverter();
    
    protected CmmnXmlConverter cmmnXmlConverter = new CmmnXmlConverter();
    protected CmmnJsonConverter cmmnJsonConverter = new CmmnJsonConverter();

    protected DmnXMLConverter dmnXMLConverter = new DmnXMLConverter();
    protected DmnJsonConverter dmnJsonConverter = new DmnJsonConverter();

    public ResultListDataRepresentation getModels(String filter, String sort, Integer modelType, HttpServletRequest request) {

        // need to parse the filterText parameter ourselves, due to encoding issues with the default parsing.
        String filterText = null;
        List<NameValuePair> params = URLEncodedUtils.parse(request.getQueryString(), StandardCharsets.UTF_8);
        if (params != null) {
            for (NameValuePair nameValuePair : params) {
                if ("filterText".equalsIgnoreCase(nameValuePair.getName())) {
                    filterText = nameValuePair.getValue();
                }
            }
        }

        List<ModelRepresentation> resultList = new ArrayList<>();
        List<Model> models = null;

        String validFilter = makeValidFilterText(filterText);

        if (validFilter != null) {
            models = modelRepository.findByModelTypeAndFilter(modelType, validFilter, sort);

        } else {
            models = modelRepository.findByModelType(modelType, sort);
        }

        if (CollectionUtils.isNotEmpty(models)) {
            List<String> addedModelIds = new ArrayList<>();
            for (Model model : models) {
                if (!addedModelIds.contains(model.getId())) {
                    addedModelIds.add(model.getId());
                    ModelRepresentation representation = createModelRepresentation(model);
                    resultList.add(representation);
                }
            }
        }

        ResultListDataRepresentation result = new ResultListDataRepresentation(resultList);
        return result;
    }

    public ResultListDataRepresentation getModelsToIncludeInAppDefinition() {

        List<ModelRepresentation> resultList = new ArrayList<>();

        List<String> addedModelIds = new ArrayList<>();
        List<Model> models = modelRepository.findByModelType(AbstractModel.MODEL_TYPE_BPMN, ModelSort.MODIFIED_DESC);

        if (CollectionUtils.isNotEmpty(models)) {
            for (Model model : models) {
                if (!addedModelIds.contains(model.getId())) {
                    addedModelIds.add(model.getId());
                    ModelRepresentation representation = createModelRepresentation(model);
                    resultList.add(representation);
                }
            }
        }

        ResultListDataRepresentation result = new ResultListDataRepresentation(resultList);
        return result;
    }
    
    public ResultListDataRepresentation getCmmnModelsToIncludeInAppDefinition() {

        List<ModelRepresentation> resultList = new ArrayList<>();

        List<String> addedModelIds = new ArrayList<>();
        List<Model> models = modelRepository.findByModelType(AbstractModel.MODEL_TYPE_CMMN, ModelSort.MODIFIED_DESC);

        if (CollectionUtils.isNotEmpty(models)) {
            for (Model model : models) {
                if (!addedModelIds.contains(model.getId())) {
                    addedModelIds.add(model.getId());
                    ModelRepresentation representation = createModelRepresentation(model);
                    resultList.add(representation);
                }
            }
        }

        ResultListDataRepresentation result = new ResultListDataRepresentation(resultList);
        return result;
    }

    public ModelRepresentation importProcessModel(HttpServletRequest request, MultipartFile file) {
        return importProcessModel(request, file, false);
    }

    public ModelRepresentation importProcessModel(HttpServletRequest request, MultipartFile file, boolean importAsNewVersionIfKeysAreMatching) {

        String fileName = file.getOriginalFilename();
        if (fileName != null && (fileName.endsWith(".bpmn") || fileName.endsWith(".bpmn20.xml"))) {
            try {
                XMLInputFactory xif = XmlUtil.createSafeXmlInputFactory();
                InputStreamReader xmlIn = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
                XMLStreamReader xtr = xif.createXMLStreamReader(xmlIn);
                BpmnModel bpmnModel = bpmnXmlConverter.convertToBpmnModel(xtr);
                if (CollectionUtils.isEmpty(bpmnModel.getProcesses())) {
                    throw new BadRequestException("No process found in definition " + fileName);
                }

                if (bpmnModel.getLocationMap().size() == 0) {
                    BpmnAutoLayout bpmnLayout = new BpmnAutoLayout(bpmnModel);
                    bpmnLayout.execute();
                }

                ObjectNode modelNode = bpmnJsonConverter.convertToJson(bpmnModel);
                org.flowable.bpmn.model.Process process = bpmnModel.getMainProcess();
                final List<Model> existingModels = modelRepository.findByKeyAndType(process.getId(), AbstractModel.MODEL_TYPE_BPMN);

                if (!importAsNewVersionIfKeysAreMatching) {

                    if (existingModels.size() > 0) {
                        throw new ConflictingRequestException("Provided model key already exists: " + process.getId());
                    }

                    final String name = StringUtils.isNotEmpty(process.getName())
                            ? process.getName()
                            : process.getId();

                    final ModelRepresentation modelRepresentation = new ModelRepresentation();
                    modelRepresentation.setKey(process.getId());
                    modelRepresentation.setName(name);
                    modelRepresentation.setDescription(process.getDocumentation());
                    modelRepresentation.setModelType(AbstractModel.MODEL_TYPE_BPMN);
                    final Model newModel = modelService.createModel(
                            modelRepresentation,
                            modelNode.toString(),
                            SecurityUtils.getCurrentUserId());
                    return new ModelRepresentation(newModel);

                } else {

                    if (existingModels.size() == 0) {
                        throw new ConflictingRequestException("Provided model for version import not available: " + process.getId());
                    }
                    if (existingModels.size() > 1) {
                        throw new ConflictingRequestException("Provided model key is not unique: " + process.getId());
                    }

                    final Model existingModelToAddNewVersionTo = existingModels.get(0);
                    final Model savedModel = modelService.saveModel(
                            existingModelToAddNewVersionTo.getId(),
                            existingModelToAddNewVersionTo.getName(),
                            existingModelToAddNewVersionTo.getKey(),
                            existingModelToAddNewVersionTo.getDescription(),
                            modelNode.toString(),
                            true,
                            "Version import via Modeler App",
                            SecurityUtils.getCurrentUserId());
                    return new ModelRepresentation(savedModel);
                }

            } catch (BadRequestException e) {
                throw e;

            } catch (Exception e) {
                LOGGER.error("Import failed for {}", fileName, e);
                throw new BadRequestException("Import failed for " + fileName + ", error message " + e.getMessage());
            }
        } else {
            throw new BadRequestException("Invalid file name, only .bpmn and .bpmn20.xml files are supported not " + fileName);
        }
    }
    
    public ModelRepresentation importCaseModel(HttpServletRequest request, MultipartFile file) {

        String fileName = file.getOriginalFilename();
        if (fileName != null && (fileName.endsWith(".cmmn") || fileName.endsWith(".cmmn.xml"))) {
            try {
                XMLInputFactory xif = XmlUtil.createSafeXmlInputFactory();
                InputStreamReader xmlIn = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
                XMLStreamReader xtr = xif.createXMLStreamReader(xmlIn);
                CmmnModel cmmnModel = cmmnXmlConverter.convertToCmmnModel(xtr);
                if (CollectionUtils.isEmpty(cmmnModel.getCases())) {
                    throw new BadRequestException("No cases found in definition " + fileName);
                }

                if (cmmnModel.getLocationMap().size() == 0) {
                    throw new BadRequestException("No CMMN DI found in definition " + fileName);
                }

                ObjectNode modelNode = cmmnJsonConverter.convertToJson(cmmnModel);

                Case caseModel = cmmnModel.getPrimaryCase();
                String name = caseModel.getId();
                if (StringUtils.isNotEmpty(caseModel.getName())) {
                    name = caseModel.getName();
                }
                String description = caseModel.getDocumentation();

                ModelRepresentation model = new ModelRepresentation();
                model.setKey(caseModel.getId());
                model.setName(name);
                model.setDescription(description);
                model.setModelType(AbstractModel.MODEL_TYPE_CMMN);
                Model newModel = modelService.createModel(model, modelNode.toString(), SecurityUtils.getCurrentUserId());
                return new ModelRepresentation(newModel);

            } catch (BadRequestException e) {
                throw e;

            } catch (Exception e) {
                LOGGER.error("Import failed for {}", fileName, e);
                throw new BadRequestException("Import failed for " + fileName + ", error message " + e.getMessage());
            }
        } else {
            throw new BadRequestException("Invalid file name, only .cmmn and .cmmn.xml files are supported not " + fileName);
        }
    }

    public ModelRepresentation importDecisionServiceModel(HttpServletRequest request, MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName != null && (fileName.endsWith(".dmn") || fileName.endsWith(".dmn.xml"))) {
            try {
                XMLInputFactory xif = XmlUtil.createSafeXmlInputFactory();
                InputStreamReader xmlIn = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
                XMLStreamReader xtr = xif.createXMLStreamReader(xmlIn);
                DmnDefinition dmnDefinition = dmnXMLConverter.convertToDmnModel(xtr);
                if (CollectionUtils.isEmpty(dmnDefinition.getDecisionServices())) {
                    throw new BadRequestException("No decision services found in definition " + fileName);
                }

                if (dmnDefinition.getLocationMap().size() == 0) {
                    throw new BadRequestException("No DMN DI found in definition " + fileName);
                }

                ConverterContext converterContext = new ConverterContext(modelService, objectMapper);
                parseDecisionTables(dmnDefinition, converterContext);
                importDecisionTables(converterContext);

                ObjectNode modelNode = dmnJsonConverter.convertToJson(dmnDefinition, converterContext);

                DecisionService firstDecisionService = dmnDefinition.getDecisionServices().iterator().next();
                String name = firstDecisionService.getId();
                if (StringUtils.isNotEmpty(firstDecisionService.getName())) {
                    name = firstDecisionService.getName();
                }

                ModelRepresentation model = new ModelRepresentation();
                model.setName(name);
                //TODO: empty for now; needs to be part of the JSON model conversion
                model.setDescription("");
                model.setKey(firstDecisionService.getId());
                model.setModelType(AbstractModel.MODEL_TYPE_DECISION_SERVICE);
                Model newModel = modelService.createModel(model, modelNode.toString(), SecurityUtils.getCurrentUserId());
                return new ModelRepresentation(newModel);

            } catch (BadRequestException e) {
                throw e;

            } catch (Exception e) {
                LOGGER.error("Import failed for {}", fileName, e);
                throw new BadRequestException("Import failed for " + fileName + ", error message " + e.getMessage());
            }
        } else {
            throw new BadRequestException("Invalid file name, only .dmn and .dmn.xml files are supported not " + fileName);
        }
    }

    protected void parseDecisionTables(DmnDefinition dmnDefinition, ConverterContext converterContext) {
        dmnDefinition.getDecisions().forEach(
            decision -> {
                String decisionNode = dmnJsonConverter.convertDecisionDecisionTableToJson(decision, "definition_" + decision.getId(),
                    decision.getName(), null).toString();
                converterContext.getDecisionTableKeyToJsonStringMap().put(decision.getId(), decisionNode);
            }
        );
    }

    protected void importDecisionTables(ConverterContext converterContext) {
        Map<String, String> decisionTableMap = converterContext.getDecisionTableKeyToJsonStringMap();
        String currentUserId = SecurityUtils.getCurrentUserId();

        for (String decisionTableKey : decisionTableMap.keySet()) {
            String decisionTableJson = decisionTableMap.get(decisionTableKey);

            JsonNode decisionTableNode;
            try {
                decisionTableNode = objectMapper.readTree(decisionTableJson);
            } catch (Exception e) {
                LOGGER.error("Error reading decision model json", e);
                throw new InternalServerErrorException("Error reading decision model json");
            }
            Model decisionTableModel = new Model();
            decisionTableModel.setName(decisionTableNode.get("name").asText());
            decisionTableModel.setKey(decisionTableNode.get("key").asText());

            String oldDecisionTableId = decisionTableNode.get("id").asText();

            JsonNode descriptionNode = decisionTableNode.get("description");
            if (descriptionNode != null && !descriptionNode.isNull()) {
                decisionTableModel.setDescription(descriptionNode.asText());
            }

            decisionTableModel.setModelEditorJson(decisionTableNode.toString());
            decisionTableModel.setModelType(AbstractModel.MODEL_TYPE_DECISION_TABLE);

            // migrate to new version
            DecisionTableModelConversionUtil.convertModelToV3(decisionTableModel);

            // persist
            Model updatedDecisionTableModel = modelService.createModel(decisionTableModel, currentUserId);

            converterContext.addDecisionTableModel(updatedDecisionTableModel, oldDecisionTableId);
        }
    }

    protected ModelRepresentation createModelRepresentation(AbstractModel model) {
        ModelRepresentation representation = null;
        if (model.getModelType() != null && model.getModelType() == 3) {
            representation = new AppDefinitionListModelRepresentation(model);

            AppDefinition appDefinition = null;
            try {
                appDefinition = objectMapper.readValue(model.getModelEditorJson(), AppDefinition.class);
            } catch (Exception e) {
                LOGGER.error("Error deserializing app {}", model.getId(), e);
                throw new InternalServerErrorException("Could not deserialize app definition");
            }
            ((AppDefinitionListModelRepresentation) representation).setAppDefinition(appDefinition);

        } else {
            representation = new ModelRepresentation(model);
        }
        return representation;
    }

    protected String makeValidFilterText(String filterText) {
        String validFilter = null;

        if (filterText != null) {
            String trimmed = StringUtils.trim(filterText);
            if (trimmed.length() >= MIN_FILTER_LENGTH) {
                validFilter = "%" + trimmed.toLowerCase() + "%";
            }
        }
        return validFilter;
    }

}
