<template>
  <div class="settings-placeholder">
    <el-empty description="功能开发中，敬请期待">
      <template #image>
        <el-icon :size="80" color="#9ca3af"><Setting /></el-icon>
      </template>
    </el-empty>
  </div>
</template>

<script setup>
import { Setting } from '@element-plus/icons-vue'
</script>

<style lang="less" scoped>
.settings-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}
</style>
