import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/chat',
    children: [
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/chat/index.vue'),
        meta: { title: '智能问数', requiresAuth: true }
      },
      {
        path: 'datasource',
        name: 'DataSource',
        component: () => import('@/views/datasource/index.vue'),
        meta: { title: '数据源管理', requiresAuth: true }
      },
      {
        path: 'datamodeling',
        name: 'DataModeling',
        component: () => import('@/views/datamodeling/index.vue'),
        meta: { title: '数据建模', requiresAuth: true }
      },
      {
        path: 'settings/profile',
        name: 'Profile',
        component: () => import('@/views/settings/profile/index.vue'),
        meta: { title: '个人设置', requiresAuth: true }
      },
      {
        path: 'settings/member',
        name: 'MemberManagement',
        component: () => import('@/views/settings/placeholder.vue'),
        meta: { title: '成员管理', requiresAuth: true }
      },
      {
        path: 'settings/permission',
        name: 'PermissionConfig',
        component: () => import('@/views/settings/placeholder.vue'),
        meta: { title: '权限配置', requiresAuth: true }
      },
      {
        path: 'settings/terminology',
        name: 'TerminologyConfig',
        component: () => import('@/views/settings/placeholder.vue'),
        meta: { title: '术语配置', requiresAuth: true }
      },
      {
        path: 'settings/sql-examples',
        name: 'SqlExamples',
        component: () => import('@/views/settings/placeholder.vue'),
        meta: { title: 'SQL示例库', requiresAuth: true }
      },
      {
        path: 'settings/ai-model',
        name: 'AiModelConfig',
        component: () => import('@/views/settings/ai-model/index.vue'),
        meta: { title: 'AI 模型配置', requiresAuth: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  
  if (to.meta.requiresAuth !== false && !userStore.isLoggedIn) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else if (to.name === 'Login' && userStore.isLoggedIn) {
    next({ name: 'Chat' })
  } else {
    next()
  }
})

export default router
