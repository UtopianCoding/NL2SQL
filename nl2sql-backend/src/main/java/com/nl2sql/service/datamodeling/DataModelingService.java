package com.nl2sql.service.datamodeling;

import com.nl2sql.model.dto.ERDiagramDTO;
import com.nl2sql.model.dto.TableRelationDTO;
import com.nl2sql.model.entity.SyncTask;

import java.util.Map;

public interface DataModelingService {

    ERDiagramDTO getERDiagram(Long dsId);

    Long analyzeRelationsAsync(Long dsId);

    SyncTask getAnalysisProgress(Long taskId);

    void deleteRelation(Long relationId);

    void updateFieldComment(Long fieldId, String comment);

    void updateRelation(Long relationId, String relationType);

    void createRelation(Long dsId, Long sourceTableId, Long targetTableId,
                        String relationType, String sourceFields, String targetFields);
}
