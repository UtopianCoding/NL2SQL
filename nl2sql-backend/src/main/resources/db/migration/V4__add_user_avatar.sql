-- 用户表添加头像字段
ALTER TABLE sys_user ADD COLUMN avatar VARCHAR(500) COMMENT '头像URL' AFTER nickname;

-- 设置默认头像
UPDATE sys_user SET avatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png' WHERE avatar IS NULL;
