<template>
  <div class="login-page">
    <!-- 左侧品牌区 -->
    <div class="brand-section">
      <div class="brand-content">
        <div class="brand-icon">
          <el-icon :size="60"><DataAnalysis /></el-icon>
        </div>
        <h1 class="brand-title">智能数据管理平台</h1>
        <p class="brand-desc">专业的企业级数据管理解决方案</p>
        <p class="brand-desc">让数据管理更简单、更高效</p>
        
        <div class="features">
          <div class="feature-item">
            <el-icon :size="28"><TrendCharts /></el-icon>
            <span>智能分析</span>
          </div>
          <div class="feature-item">
            <el-icon :size="28"><Document /></el-icon>
            <span>报表生成</span>
          </div>
          <div class="feature-item">
            <el-icon :size="28"><Setting /></el-icon>
            <span>灵活配置</span>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 右侧登录表单 -->
    <div class="form-section">
      <div class="form-container">
        <h2 class="form-title">欢迎登录</h2>
        <p class="form-subtitle">请输入您的账户信息进行登录</p>
        
        <el-form
          ref="formRef"
          :model="formData"
          :rules="rules"
          class="login-form"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="email" label="邮箱/手机号">
            <el-input
              v-model="formData.email"
              placeholder="请输入邮箱或手机号"
              :prefix-icon="Message"
              size="large"
            />
          </el-form-item>
          
          <el-form-item prop="username" label="用户名">
            <el-input
              v-model="formData.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              size="large"
            />
          </el-form-item>
          
          <el-form-item prop="password" label="密码">
            <el-input
              v-model="formData.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              size="large"
              show-password
            />
          </el-form-item>
          
          <div class="form-options">
            <el-checkbox v-model="formData.rememberMe">记住密码</el-checkbox>
            <el-link type="primary" :underline="false">忘记密码？</el-link>
          </div>
          
          <el-form-item>
            <el-button
              type="primary"
              size="large"
              :loading="loading"
              class="login-btn"
              @click="handleLogin"
            >
              登录
            </el-button>
          </el-form-item>
          
          <div class="register-link">
            还没有账户？
            <el-link type="primary" :underline="false">立即注册</el-link>
          </div>
        </el-form>
        
        <div class="copyright">
          &copy; 2024 智能数据管理平台 All Rights Reserved
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { User, Lock, Message } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)

const formData = reactive({
  email: '',
  username: '',
  password: '',
  rememberMe: false
})

const rules = {
  email: [
    { required: true, message: '请输入邮箱或手机号', trigger: 'blur' }
  ],
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    
    loading.value = true
    try {
      await userStore.login({
        username: formData.username,
        password: formData.password,
        email: formData.email,
        rememberMe: formData.rememberMe
      })
      
      ElMessage.success('登录成功')
      const redirect = route.query.redirect || '/chat'
      router.push(redirect)
    } catch (error) {
      ElMessage.error(error.message || '登录失败')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style lang="less" scoped>
.login-page {
  display: flex;
  height: 100vh;
  
  .brand-section {
    flex: 0 0 400px;
    background: linear-gradient(135deg, #2e7df7 0%, #1e5fb3 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    position: relative;
    overflow: hidden;
    
    &::before {
      content: '';
      position: absolute;
      width: 200px;
      height: 200px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.1);
      top: -50px;
      left: -50px;
    }
    
    &::after {
      content: '';
      position: absolute;
      width: 150px;
      height: 150px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.05);
      bottom: 100px;
      right: -30px;
    }
    
    .brand-content {
      text-align: center;
      z-index: 1;
      
      .brand-icon {
        margin-bottom: 20px;
      }
      
      .brand-title {
        font-size: 28px;
        font-weight: 600;
        margin-bottom: 15px;
      }
      
      .brand-desc {
        font-size: 14px;
        opacity: 0.9;
        margin: 5px 0;
      }
      
      .features {
        display: flex;
        gap: 30px;
        margin-top: 40px;
        
        .feature-item {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 8px;
          font-size: 12px;
        }
      }
    }
  }
  
  .form-section {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #fff;
    
    .form-container {
      width: 400px;
      padding: 40px;
      
      .form-title {
        font-size: 28px;
        font-weight: 600;
        color: var(--text-primary);
        margin-bottom: 10px;
      }
      
      .form-subtitle {
        font-size: 14px;
        color: var(--text-secondary);
        margin-bottom: 40px;
      }
      
      .login-form {
        .el-form-item {
          margin-bottom: 24px;
        }
        
        .form-options {
          display: flex;
          justify-content: space-between;
          margin-bottom: 24px;
        }
        
        .login-btn {
          width: 100%;
          height: 48px;
          font-size: 16px;
        }
        
        .register-link {
          text-align: center;
          color: var(--text-secondary);
          font-size: 14px;
        }
      }
      
      .copyright {
        margin-top: 60px;
        text-align: center;
        font-size: 12px;
        color: var(--text-secondary);
      }
    }
  }
}
</style>
