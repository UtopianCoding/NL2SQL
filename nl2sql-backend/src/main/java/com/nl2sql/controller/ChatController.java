package com.nl2sql.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nl2sql.common.Result;
import com.nl2sql.model.dto.QueryRequest;
import com.nl2sql.model.dto.QueryResponse;
import com.nl2sql.model.entity.Conversation;
import com.nl2sql.model.entity.ChatMessage;
import com.nl2sql.model.entity.QueryHistory;
import com.nl2sql.mapper.ChatMessageMapper;
import com.nl2sql.mapper.ConversationMapper;
import com.nl2sql.mapper.QueryHistoryMapper;
import com.nl2sql.service.nl2sql.NL2SqlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "智能问答", description = "NL2SQL核心功能、会话管理、历史记录")
public class ChatController {

    @Autowired
    private NL2SqlService nl2SqlService;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private QueryHistoryMapper queryHistoryMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @PostMapping("/nl2sql/query")
    @Operation(summary = "执行自然语言查询")
    public Result<QueryResponse> query(@Valid @RequestBody QueryRequest request, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        QueryResponse response = nl2SqlService.query(request, userId);
        return Result.success(response);
    }

    @PostMapping("/conversation/create")
    @Operation(summary = "创建新会话")
    public Result<Conversation> createConversation(@RequestParam Long dsId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setDsId(dsId);
        conversation.setStatus(1);
        conversation.setTurnCount(0);
        conversationMapper.insert(conversation);
        return Result.success(conversation);
    }

    @GetMapping("/conversation/list")
    @Operation(summary = "获取会话列表")
    public Result<Page<Conversation>> listConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Page<Conversation> pageParam = new Page<>(page, size);
        Page<Conversation> conversations = conversationMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, userId)
                        .orderByDesc(Conversation::getUpdateTime));
        return Result.success(conversations);
    }

    @GetMapping("/conversation/{id}")
    @Operation(summary = "获取会话详情及历史记录")
    public Result<ConversationDetail> getConversation(@PathVariable Long id) {
        Conversation conversation = conversationMapper.selectById(id);
        if (conversation == null) {
            throw new RuntimeException("会话不存在");
        }
        List<ChatMessage> chatMessages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, id)
                        .orderByAsc(ChatMessage::getCreateTime));
        List<QueryHistory> histories = queryHistoryMapper.selectList(
                new LambdaQueryWrapper<QueryHistory>()
                        .eq(QueryHistory::getConversationId, id)
                        .orderByAsc(QueryHistory::getCreateTime));
        return Result.success(new ConversationDetail(conversation, histories, chatMessages));
    }

    @DeleteMapping("/conversation/{id}")
    @Operation(summary = "删除会话")
    public Result<Void> deleteConversation(@PathVariable Long id) {
        conversationMapper.deleteById(id);
        return Result.success();
    }

    @GetMapping("/history/list")
    @Operation(summary = "获取历史记录列表")
    public Result<Page<QueryHistory>> listHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Page<QueryHistory> pageParam = new Page<>(page, size);
        Page<QueryHistory> histories = queryHistoryMapper.selectPage(pageParam,
                new LambdaQueryWrapper<QueryHistory>()
                        .eq(QueryHistory::getUserId, userId)
                        .orderByDesc(QueryHistory::getCreateTime));
        return Result.success(histories);
    }

    @PostMapping("/history/{id}/favorite")
    @Operation(summary = "收藏/取消收藏")
    public Result<Void> toggleFavorite(@PathVariable Long id) {
        QueryHistory history = queryHistoryMapper.selectById(id);
        if (history == null) {
            throw new RuntimeException("记录不存在");
        }
        history.setIsFavorite(history.getIsFavorite() == 1 ? 0 : 1);
        queryHistoryMapper.updateById(history);
        return Result.success();
    }

    @GetMapping("/history/favorites")
    @Operation(summary = "获取收藏列表")
    public Result<List<QueryHistory>> getFavorites(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<QueryHistory> favorites = queryHistoryMapper.selectList(
                new LambdaQueryWrapper<QueryHistory>()
                        .eq(QueryHistory::getUserId, userId)
                        .eq(QueryHistory::getIsFavorite, 1)
                        .orderByDesc(QueryHistory::getCreateTime));
        return Result.success(favorites);
    }

    public record ConversationDetail(Conversation conversation, List<QueryHistory> histories, List<ChatMessage> messages) {}
}
