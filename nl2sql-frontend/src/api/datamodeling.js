import request from '@/utils/request'

export const datamodelingApi = {
  getERDiagram(dsId) {
    return request.get(`/datamodeling/${dsId}/er-diagram`)
  },

  analyze(dsId) {
    return request.post(`/datamodeling/${dsId}/analyze`)
  },

  getAnalysisProgress(taskId) {
    return request.get(`/datamodeling/analyze-progress/${taskId}`)
  },

  deleteRelation(relationId) {
    return request.delete(`/datamodeling/relation/${relationId}`)
  },

  updateFieldComment(fieldId, comment) {
    return request.put(`/datamodeling/field/${fieldId}/comment`, { comment })
  },

  updateRelation(relationId, relationType) {
    return request.put(`/datamodeling/relation/${relationId}`, { relationType })
  },

  createRelation(dsId, data) {
    return request.post(`/datamodeling/${dsId}/relation`, data)
  }
}
