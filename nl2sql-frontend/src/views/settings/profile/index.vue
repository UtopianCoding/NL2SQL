<template>
  <div class="profile-page">
    <div class="page-header">
      <h2>个人设置</h2>
      <p>管理您的个人信息和账户安全</p>
    </div>

    <div class="profile-content">
      <!-- 基本信息卡片 -->
      <el-card class="info-card">
        <template #header>
          <div class="card-header">
            <span>基本信息</span>
          </div>
        </template>

        <el-form
          ref="profileFormRef"
          :model="profileForm"
          :rules="profileRules"
          label-width="100px"
          class="profile-form"
        >
          <!-- 头像 -->
          <el-form-item label="头像">
            <div class="avatar-section">
              <el-avatar :size="80" :src="avatarPreview || defaultAvatar">
                {{ userInitial }}
              </el-avatar>
              <div class="avatar-actions">
                <el-input
                  v-model="profileForm.avatar"
                  placeholder="输入头像URL"
                  class="avatar-input"
                  @input="onAvatarUrlChange"
                />
                <div class="avatar-hint">请输入有效的图片URL地址</div>
              </div>
            </div>
          </el-form-item>

          <el-form-item label="用户名">
            <el-input :value="userStore.userInfo?.username" disabled>
              <template #suffix>
                <el-tooltip content="用户名不可修改">
                  <el-icon><Lock /></el-icon>
                </el-tooltip>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="profileForm.nickname" placeholder="请输入昵称" maxlength="50" show-word-limit />
          </el-form-item>

          <el-form-item label="邮箱" prop="email">
            <el-input v-model="profileForm.email" placeholder="请输入邮箱" maxlength="100" />
          </el-form-item>

          <el-form-item label="手机号" prop="phone">
            <el-input v-model="profileForm.phone" placeholder="请输入手机号" maxlength="20" />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="profileLoading" @click="saveProfile">
              保存修改
            </el-button>
            <el-button @click="resetProfileForm">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 修改密码卡片 -->
      <el-card class="password-card">
        <template #header>
          <div class="card-header">
            <span>修改密码</span>
          </div>
        </template>

        <el-form
          ref="passwordFormRef"
          :model="passwordForm"
          :rules="passwordRules"
          label-width="100px"
          class="password-form"
        >
          <el-form-item label="当前密码" prop="oldPassword">
            <el-input
              v-model="passwordForm.oldPassword"
              type="password"
              placeholder="请输入当前密码"
              show-password
            />
          </el-form-item>

          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="passwordForm.newPassword"
              type="password"
              placeholder="请输入新密码（6-50位）"
              show-password
            />
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="passwordForm.confirmPassword"
              type="password"
              placeholder="请再次输入新密码"
              show-password
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="passwordLoading" @click="changePassword">
              修改密码
            </el-button>
            <el-button @click="resetPasswordForm">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'

const userStore = useUserStore()

const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

const profileFormRef = ref()
const passwordFormRef = ref()
const profileLoading = ref(false)
const passwordLoading = ref(false)
const avatarPreview = ref('')

const userInitial = computed(() => {
  const name = userStore.userInfo?.nickname || userStore.userInfo?.username || 'U'
  return name.charAt(0).toUpperCase()
})

// 基本信息表单
const profileForm = reactive({
  nickname: '',
  email: '',
  phone: '',
  avatar: ''
})

// 密码表单
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 表单验证规则
const profileRules = {
  nickname: [
    { max: 50, message: '昵称长度不能超过50个字符', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ]
}

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const passwordRules = {
  oldPassword: [
    { required: true, message: '请输入当前密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 50, message: '密码长度必须在6-50位之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

// 初始化表单数据
const initProfileForm = () => {
  const userInfo = userStore.userInfo
  if (userInfo) {
    profileForm.nickname = userInfo.nickname || ''
    profileForm.email = userInfo.email || ''
    profileForm.phone = userInfo.phone || ''
    profileForm.avatar = userInfo.avatar || ''
    avatarPreview.value = userInfo.avatar || ''
  }
}

// 头像URL变化时更新预览
const onAvatarUrlChange = (value) => {
  avatarPreview.value = value
}

// 保存基本信息
const saveProfile = async () => {
  try {
    await profileFormRef.value.validate()
    profileLoading.value = true
    
    await userStore.updateProfile({
      nickname: profileForm.nickname,
      email: profileForm.email,
      phone: profileForm.phone,
      avatar: profileForm.avatar
    })
    
    ElMessage.success('保存成功')
  } catch (error) {
    if (error !== false) {
      ElMessage.error(error.message || '保存失败')
    }
  } finally {
    profileLoading.value = false
  }
}

// 重置基本信息表单
const resetProfileForm = () => {
  initProfileForm()
}

// 修改密码
const changePassword = async () => {
  try {
    await passwordFormRef.value.validate()
    passwordLoading.value = true
    
    await userStore.changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
      confirmPassword: passwordForm.confirmPassword
    })
    
    ElMessage.success('密码修改成功')
    resetPasswordForm()
  } catch (error) {
    if (error !== false) {
      ElMessage.error(error.message || '密码修改失败')
    }
  } finally {
    passwordLoading.value = false
  }
}

// 重置密码表单
const resetPasswordForm = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.resetFields()
}

onMounted(() => {
  initProfileForm()
})
</script>

<style lang="less" scoped>
.profile-page {
  max-width: 800px;
  margin: 0 auto;

  .page-header {
    margin-bottom: 24px;

    h2 {
      margin: 0 0 8px 0;
      font-size: 24px;
      font-weight: 600;
      color: #1f2937;
    }

    p {
      margin: 0;
      color: #6b7280;
      font-size: 14px;
    }
  }

  .profile-content {
    display: flex;
    flex-direction: column;
    gap: 24px;
  }

  .info-card,
  .password-card {
    .card-header {
      font-size: 16px;
      font-weight: 600;
      color: #374151;
    }
  }

  .profile-form,
  .password-form {
    max-width: 500px;

    :deep(.el-form-item) {
      margin-bottom: 24px;
    }
  }

  .avatar-section {
    display: flex;
    align-items: flex-start;
    gap: 20px;

    .avatar-actions {
      flex: 1;

      .avatar-input {
        margin-bottom: 8px;
      }

      .avatar-hint {
        font-size: 12px;
        color: #9ca3af;
      }
    }
  }
}
</style>
