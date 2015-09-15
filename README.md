# work
1.碰到一个关于 sd卡安装app 与直接安装会splashActivity 与guideActivity 按home键后 回不到当前页的问题

通过下面代码判断 是栈底
if (!isTaskRoot()) {
			final Intent intent = getIntent();
			if (intent.hasCategory(Intent.CATEGORY_LAUNCHER) && Intent.ACTION_MAIN.equals(intent.getAction())) {
				Log.w("SplashActivity", "Main Activity is not the root.  Finishing Main Activity instead of launching.");
				finish();
				return;
			}
		}
		
参见问题描述
http://www.ithao123.cn/content-1356144.html
