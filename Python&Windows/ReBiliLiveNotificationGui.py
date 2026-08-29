import tkinter as tk
import ttkbootstrap as ttk
from ttkbootstrap.dialogs import Messagebox as tkmb
import tkinter.font as tkfont
import webbrowser
from retrying import retry
import time
import pystray
from pystray import MenuItem, Menu
from PIL import Image, ImageTk, ImageDraw
from icon_module import create_default_avatar
import threading
import requests
import configparser
import os
import sys
import subprocess
import win32api
import win32con
import win32event
import pythoncom
import win32com.client
from io import BytesIO
import ctypes
import urllib3
from enum import IntEnum

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# 原作者 @yunhuanyx 
# 原项目 https://github.com/yunhuanyx/biliLiveNotification
# Re版修改 https://github.com/SadYuyuko/ReBiliLiveNotification

if getattr(sys, 'frozen', False):
    app_dir = os.path.dirname(sys.executable)
else:
    app_dir = os.path.dirname(os.path.abspath(__file__))

user_config_dir = os.path.join(os.path.expanduser('~'), '.ReBiliLiveNotification')
os.makedirs(user_config_dir, exist_ok=True)
config_path = os.path.join(user_config_dir, 'ReBLN.ini')

APP_VERSION = "1.3"

# UA
headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0"}

# 单实例
_mutex_handle = None

def check_single_instance():
    global _mutex_handle
    _mutex_handle = win32event.CreateMutex(None, False, "ReBiliLiveNotification_SingleInstance")
    return win32api.GetLastError() != 183


class ListenState(IntEnum):
    STOPPED = 0
    RUNNING = 1
    PAUSED = 2

class AppState:
    def __init__(self):
        self.state = ListenState.STOPPED
        self.stop_event = threading.Event()
        self.pause_event = threading.Event()
        self.wait_event = threading.Event()
        self.notification_windows = {}
        self.streamer_info = {}
        self.rowdata = []
        self.session = requests.Session()

# 配置文件
def read_config():
    config = configparser.ConfigParser()
    if not os.path.exists(config_path):
        return config
    try:
        config.read(config_path, encoding='utf-8')
    except configparser.MissingSectionHeaderError:
        with open(config_path, 'r', encoding='utf-8') as f:
            content = f.read()
        with open(config_path, 'w', encoding='utf-8') as f:
            f.write('[DEFAULT]\n' + content)
        config.read(config_path, encoding='utf-8')
    return config

def write_config(config):
    with open(config_path, 'w', encoding='utf-8') as f:
        config.write(f)

# 线程日志
def log(message):
    root.after(0, _append_log, message)

def _append_log(message):
    timestamp = time.strftime("%m-%d %H:%M:%S", time.localtime())
    info_text.config(state=tk.NORMAL)
    info_text.insert(tk.END, f'{timestamp}   {message}\n')
    info_text.config(state=tk.DISABLED)
    info_text.see(tk.END)

# 关于
def show_about_window():
    about_window = tk.Toplevel(root)
    about_window.title(f"v{APP_VERSION}")
    about_window.resizable(False, False)
    about_window.attributes('-topmost', True)
    
    window_width = 480
    window_height = 330
    screen_width = about_window.winfo_screenwidth()
    screen_height = about_window.winfo_screenheight()
    x = (screen_width // 2) - (window_width // 2)
    y = (screen_height // 2) - (window_height // 2) - 50
    about_window.geometry(f'{window_width}x{window_height}+{x}+{y}')
    
    main_frame = ttk.Frame(about_window)
    main_frame.pack(fill=tk.BOTH, expand=True, padx=20, pady=20)
    
    title_label = ttk.Label(main_frame, text="关于 Re：B站开播提醒", 
                           font=("微软雅黑", 12, "bold"))
    title_label.pack(pady=(0, 15))
    
    text_frame = ttk.Frame(main_frame)
    text_frame.pack(fill=tk.BOTH, expand=True, pady=(0, 15))
    
    text_scrollbar = ttk.Scrollbar(text_frame, bootstyle="round")
    text_scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
    
    about_text = tk.Text(text_frame, height=6, wrap=tk.WORD,
                        font=("微软雅黑", 9),
                        yscrollcommand=text_scrollbar.set,
                        relief=tk.FLAT, bd=2, bg="#f0f0f0")
    text_scrollbar.config(command=about_text.yview)
    
    about_text.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
    
    about_content = f"""版本 v{APP_VERSION}
原作者 @yunhuanyx 
原项目 https://github.com/yunhuanyx/biliLiveNotification
Re版修改 https://github.com/SadYuyuko/ReBiliLiveNotification
配置文件位于 {config_path}
"""
    
    about_text.insert(tk.END, about_content)
    about_text.configure(state=tk.DISABLED)
    
    close_button = ttk.Button(main_frame, text="关闭", width=10,
                             command=about_window.destroy,
                             bootstyle="secondary")
    close_button.pack()
    
    about_window.bind('<Escape>', lambda e: about_window.destroy())
    
    about_window.focus_force()

# 计划任务自启动
def get_task_name():
    return 'ReBiliLiveNotification'

def get_app_path():
    if getattr(sys, 'frozen', False):
        exe_path = sys.executable
        return exe_path
    else:
        script_path = os.path.abspath(__file__)
        return sys.executable

def set_autostart(enabled):
    try:
        task_name = get_task_name()
        
        pythoncom.CoInitialize()
        
        scheduler = win32com.client.Dispatch('Schedule.Service')
        scheduler.Connect()
        
        root_folder = scheduler.GetFolder('\\')
        
        if enabled:
            task_def = scheduler.NewTask(0)
            
            reg_info = task_def.RegistrationInfo
            reg_info.Description = 'Re：B站开播提醒开机自启动'
            reg_info.Author = 'ReBiliLiveNotification'
            
            trigger = task_def.Triggers.Create(9)
            trigger.Enabled = True
            
            action = task_def.Actions.Create(0)
            app_path = get_app_path()
            
            if getattr(sys, 'frozen', False):
                action.Path = app_path
                action.Arguments = '--minimized'
                action.WorkingDirectory = os.path.dirname(app_path)
            else:
                script_path = os.path.abspath(__file__)
                python_dir = os.path.dirname(sys.executable)
                pythonw_path = os.path.join(python_dir, 'pythonw.exe')
                
                if os.path.exists(pythonw_path):
                    action.Path = pythonw_path
                    action.WorkingDirectory = os.path.dirname(script_path)
                    action.Arguments = f'"{script_path}" --minimized'
                else:
                    action.Path = sys.executable
                    action.WorkingDirectory = os.path.dirname(script_path)
                    action.Arguments = f'"{script_path}" --minimized'
            
            settings = task_def.Settings
            settings.Enabled = True
            settings.Hidden = False
            settings.RunOnlyIfIdle = False
            settings.DisallowStartIfOnBatteries = False
            settings.StopIfGoingOnBatteries = False
            settings.StartWhenAvailable = False
            settings.AllowHardTerminate = True
            settings.WakeToRun = False
            settings.Priority = 7
            
            task_def.Principal.RunLevel = 1
            
            root_folder.RegisterTaskDefinition(
                task_name,
                task_def,
                6,
                None,
                None,
                3
            )
            
            print(f"已设置开机自启动计划任务: {task_name}")
            return True
            
        else:
            try:
                root_folder.DeleteTask(task_name, 0)
                print(f"已删除开机自启动计划任务: {task_name}")
                return True
            except Exception as e:
                if '80070002' in str(e):
                    print(f"计划任务 {task_name} 不存在，无需删除")
                    return True
                else:
                    raise
                    
    except Exception as e:
        print(f"设置开机自启动计划任务失败: {e}")
        return False
    finally:
        try:
            pythoncom.CoUninitialize()
        except Exception:
            pass

def is_autostart_enabled():
    try:
        task_name = get_task_name()
        
        cmd = f'schtasks /Query /TN "{task_name}"'
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        
        if result.returncode == 0:
            return True
        else:
            return False

    except Exception as e:
        print(f"检查开机自启动状态失败: {e}")
        return False

def is_minimized_start():
    return '--minimized' in sys.argv

def center_window(w, h):
    ws = root.winfo_screenwidth()
    hs = root.winfo_screenheight()
    x = (ws/2) - (w/2) - 20
    y = (hs/2) - (h/2) - 20
    root.geometry('%dx%d+%d+%d' % (w, h, x, y))

def show_window():
    root.deiconify()
    root.lift()
    root.focus_force()

def check_for_updates():
    def _check():
        try:
            url = "https://api.github.com/repos/SadYuyuko/ReBiliLiveNotification/releases/latest"
            resp = state.session.get(url, headers=headers, timeout=10, verify=False)
            resp.raise_for_status()
            data = resp.json()
            latest_tag = data['tag_name']
            body = data.get('body', '').strip()
            msg = f"最新版本: {latest_tag}"
            if body:
                msg += f"\n\n更新内容:\n{body}"
            msg += "\n\n点击确定前往下载页面"

            log(f"检查更新：发现版本 {latest_tag}")

            def _ask(data=data, msg=msg, tag=latest_tag):
                result = tkmb.show_question(title="检查更新", message=msg)
                if result:
                    webbrowser.open(data['html_url'])
                    log(f"检查更新：已前往下载 {tag}")
            root.after(0, _ask)

        except Exception as e:
            log(f"检查更新失败：{str(e)}")
            def _error(e=e):
                tkmb.show_error(title="检查更新", message=f"检查更新失败: {str(e)}")
            root.after(0, _error)

    threading.Thread(target=_check, daemon=True).start()

def quit_window():
    for rid, win in state.notification_windows.items():
        if win and win.winfo_exists():
            try:
                win.destroy()
            except Exception:
                pass
    state.stop_event.set()
    state.wait_event.set()
    root.destroy()

def begin_listen():
    room_id_text = room_id_entry.get('1.0', tk.END).strip()
    room_id_text = room_id_text.replace('，', ',').replace('\n', '').replace(' ', '')
    if not room_id_text:
        root.after(0, lambda: tkmb.show_error(title="错误", message="房间号为空，请保存设置后再开始！"))
        return
    
    roomID = [rid for rid in room_id_text.split(',') if rid]
    root.after(0, load_table_data)
    timeInterval = int(time_interval_var.get())
    roomID_dic = {rid: False for rid in roomID}
    
    state.state = ListenState.RUNNING
    listen.configure(text="暂停检测", bootstyle="warning-outline")
    stopl.configure(state="normal")
    stateStr.set("状态：检测中")
    log("开始检测...")
    
    threading.Thread(target=listen_main, args=(roomID, roomID_dic, timeInterval), daemon=True).start()

def listen_thread():
    if state.state == ListenState.STOPPED:
        begin_listen()
    elif state.state == ListenState.RUNNING:
        state.state = ListenState.PAUSED
        state.pause_event.clear()
        state.wait_event.set()
        listen.configure(text="恢复检测", bootstyle="outline")
        stopl.configure(state="disabled")
        stateStr.set("状态：已暂停")
    else:
        state.state = ListenState.RUNNING
        state.pause_event.set()
        listen.configure(text="暂停检测", bootstyle="warning-outline")
        stopl.configure(state="normal")
        stateStr.set("状态：检测中")
        log("已恢复检测")

def stop_close():
    quit_window()

def stop_listen():
    state.stop_event.set()
    state.wait_event.set()
    state.state = ListenState.STOPPED
    listen.configure(text='开始检测', bootstyle="outline")
    stopl.configure(state="disabled")
    stateStr.set("状态：空闲中")

@retry(stop_max_attempt_number=5)
def get_live_status(rid):
    url = api_var.get() + rid
    response = state.session.get(url, headers=headers, timeout=10)
    response.raise_for_status()
    data = response.json()
    if data['code'] != 0:
        raise RuntimeError(f'直播间 {rid} 不存在')
    return {'live_status': data['data']['live_status'], 'uid': data['data']['uid']}

@retry(stop_max_attempt_number=3)
def get_streamer_info(uid):
    url = "https://api.live.bilibili.com/live_user/v1/Master/info?uid=" + str(uid)
    response = state.session.get(url, headers=headers, timeout=10)
    response.raise_for_status()
    data = response.json()
    return {'uname': data['data']['info']['uname'], 'face': data['data']['info']['face']}

def open_live_url(rid):
    webbrowser.open(f"https://live.bilibili.com/{rid}")
    log(f"已打开直播间 {rid}")


def show_notification_window(rid, uname, uid):
    if rid in state.notification_windows and state.notification_windows[rid] and state.notification_windows[rid].winfo_exists():
        try:
            state.notification_windows[rid].destroy()
        except Exception:
            pass

    notification_window = tk.Toplevel(root)
    notification_window.title(f"{uname} 开播提醒")
    notification_window.resizable(False, False)
    notification_window.attributes('-topmost', True)

    screen_width = notification_window.winfo_screenwidth()
    screen_height = notification_window.winfo_screenheight()
    window_width = 340
    window_height = 190
    x = (screen_width // 2) - (window_width // 2)
    y = (screen_height // 2) - (window_height // 2) - 50
    notification_window.geometry(f'{window_width}x{window_height}+{x}+{y}')

    notification_window.focus_force()

    try:
        hwnd = ctypes.windll.user32.GetParent(notification_window.winfo_id())
        DWMWA_WINDOW_CORNER_PREFERENCE = 33
        DWM_WINDOW_CORNER_ROUND = 2
        ctypes.windll.dwmapi.DwmSetWindowAttribute(
            hwnd, DWMWA_WINDOW_CORNER_PREFERENCE,
            ctypes.byref(ctypes.c_int(DWM_WINDOW_CORNER_ROUND)),
            ctypes.sizeof(ctypes.c_int))
    except Exception:
        pass

    def on_key_press(event):
        if event.keysym in ('y', 'Y'):
            open_live_url(rid)
            notification_window.destroy()
        elif event.keysym in ('n', 'N'):
            notification_window.destroy()

    notification_window.bind('<KeyPress>', on_key_press)

    main_frame = ttk.Frame(notification_window, padding=15)
    main_frame.pack(fill=tk.BOTH, expand=True)

    # 头像信息
    top_frame = ttk.Frame(main_frame)
    top_frame.pack(fill=tk.X, pady=(0, 10))

    avatar_size = 64
    default_avatar = create_default_avatar(avatar_size)
    placeholder_photo = ImageTk.PhotoImage(default_avatar)

    avatar_label = tk.Label(top_frame, image=placeholder_photo, width=avatar_size, height=avatar_size)
    avatar_label.image = placeholder_photo
    avatar_label.pack(side=tk.LEFT, padx=(0, 15))

    info_frame = ttk.Frame(top_frame)
    info_frame.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

    name_label = tk.Label(info_frame, text=uname, font=("微软雅黑", 16, "bold"), fg="#00A1D6")
    name_label.pack(anchor="w", pady=(6, 0))

    room_label = tk.Label(info_frame, text=f"房间号: {rid}", font=("微软雅黑", 10), fg="#888888")
    room_label.pack(anchor="w", pady=(4, 0))

    # 按钮
    button_frame = ttk.Frame(main_frame)
    button_frame.pack(fill=tk.X)

    open_btn = ttk.Button(button_frame, text="打开直播间 (Y)", width=14,
                          command=lambda: [open_live_url(rid), notification_window.destroy()],
                          bootstyle="success")
    open_btn.pack(side=tk.LEFT, padx=(0, 10))
    ignore_btn = ttk.Button(button_frame, text="忽略 (N)", width=14,
                            command=notification_window.destroy, bootstyle="secondary")
    ignore_btn.pack(side=tk.LEFT)

    face_url = state.streamer_info.get(rid, {}).get('face', '')
    if face_url:
        def load_avatar():
            try:
                resp = state.session.get(face_url, headers=headers, timeout=10)
                img = Image.open(BytesIO(resp.content)).convert('RGBA')
                img = img.resize((avatar_size, avatar_size), Image.LANCZOS)
                mask = Image.new('L', (avatar_size, avatar_size), 0)
                ImageDraw.Draw(mask).ellipse([0, 0, avatar_size, avatar_size], fill=255)
                circular = Image.new('RGBA', (avatar_size, avatar_size), (0, 0, 0, 0))
                circular.paste(img, (0, 0), mask)
                photo = ImageTk.PhotoImage(circular)
                root.after(0, lambda p=photo: [avatar_label.configure(image=p), setattr(avatar_label, 'image', p)])
            except Exception:
                fallback = create_default_avatar(avatar_size)
                fp = ImageTk.PhotoImage(fallback)
                root.after(0, lambda p=fp: [avatar_label.configure(image=p), setattr(avatar_label, 'image', p)])

        threading.Thread(target=load_avatar, daemon=True).start()

    state.notification_windows[rid] = notification_window

    def on_close():
        if rid in state.notification_windows:
            state.notification_windows[rid] = None
        notification_window.destroy()

    notification_window.protocol("WM_DELETE_WINDOW", on_close)
    notification_window.after(600000, on_close)

def listen_main(roomID, roomID_dic, wait_time):
    i = 0
    error_flag = {rid: False for rid in roomID}
    first_run = True

    while True:
        if state.stop_event.is_set():
            raise RuntimeError("停止检测")

        if state.state == ListenState.PAUSED:
            log("已暂停检测")
            state.wait_event.clear()
            state.pause_event.wait()

        for rid in roomID[::-1]:
            try:
                live_info_dict = get_live_status(rid)
                live_status = live_info_dict['live_status']
                uid = live_info_dict['uid']

                if error_flag.get(rid, False):
                    log("网络恢复...")
                    error_flag[rid] = False
                    if rid in state.streamer_info and 'uname' in state.streamer_info[rid]:
                        uname = state.streamer_info[rid]['uname']
                    else:
                        uinfo_dict = get_streamer_info(uid)
                        uname = uinfo_dict['uname']
                        state.streamer_info[rid] = {'uid': uid, 'uname': uname, 'face': uinfo_dict.get('face', '')}
                    status_text = "直播中" if live_status == 1 else "未开播"
                    root.after(0, lambda r=rid, n=uname, s=status_text: update_table_row(r, n, s))

                if live_status == 1:
                    if first_run:
                        log("开始检测...")
                        first_run = False

                    if not roomID_dic[rid]:
                        uinfo_dict = get_streamer_info(uid)
                        uname = uinfo_dict['uname']
                        root.after(0, lambda r=rid, n=uname: update_table_row(r, n, "直播中"))
                        state.streamer_info[rid] = {'uid': uid, 'uname': uname, 'face': uinfo_dict.get('face', '')}

                        if auto_jump_var.get():
                            open_live_url(rid)
                            log(f"{uname}({rid})已开播，已自动跳转")
                        if popup_notify_var.get():
                            root.after(0, lambda r=rid, n=uname, u=uid: show_notification_window(r, n, u))
                            log(f"{uname}({rid})已开播，已显示通知窗口")

                        roomID_dic[rid] = True
                    i += 1
                else:
                    if first_run:
                        log("开始检测...")
                        first_run = False

                    if roomID_dic[rid]:
                        uinfo_dict = get_streamer_info(uid)
                        uname = uinfo_dict['uname']
                        root.after(0, lambda r=rid, n=uname: update_table_row(r, n, "未开播"))

                        win = state.notification_windows.get(rid)
                        if win and win.winfo_exists():
                            try:
                                win.destroy()
                            except Exception:
                                pass
                            state.notification_windows[rid] = None

                        log(f"{uname}({rid})已下播")
                        roomID_dic[rid] = False
                    i += 1

            except Exception as e:
                log(str(e))
                uname = state.streamer_info.get(rid, {}).get('uname', rid)
                root.after(0, lambda r=rid, n=uname: update_table_row(r, n, "错误"))
                error_flag[rid] = True
                i += 1

        if state.stop_event.is_set():
            raise RuntimeError("停止检测")

        has_errors = any(error_flag.values())
        state.wait_event.wait(10 if has_errors else wait_time)

def save_settings():
    room_id_text = room_id_entry.get('1.0', tk.END).strip().replace('，', ',').replace('\n', '').replace(' ', '')
    time_interval = time_interval_var.get()
    api_url = api_var.get().strip()

    if room_id_text:
        for rid in room_id_text.split(','):
            if not rid.isdigit():
                tkmb.show_error(title="错误", message=f"房间号 {rid} 无效，必须为数字")
                return

    try:
        time_int = int(time_interval)
        if time_int < 10:
            tkmb.show_error(title="错误", message="检测间隔不能小于10秒")
            return
    except ValueError:
        tkmb.show_error(title="错误", message="检测间隔必须为数字")
        return

    if not api_url:
        tkmb.show_error(title="错误", message="API不能为空")
        return

    config = read_config()
    config['DEFAULT']['api'] = api_url
    config['DEFAULT']['roomID'] = room_id_text
    config['DEFAULT']['timeInterval'] = str(time_int)
    config['DEFAULT']['popupNotify'] = '1' if popup_notify_var.get() else '0'
    config['DEFAULT']['autoJump'] = '1' if auto_jump_var.get() else '0'
    config['DEFAULT']['autoStartListen'] = '1' if auto_listen_var.get() else '0'
    config['DEFAULT']['autoStart'] = '1' if autostart_var.get() else '0'
    if config.has_option('DEFAULT', 'detectionAction'):
        config.remove_option('DEFAULT', 'detectionAction')
    write_config(config)

    if autostart_var.get():
        if not is_autostart_enabled():
            success = set_autostart(True)
        else:
            success = True
    else:
        if is_autostart_enabled():
            success = set_autostart(False)
        else:
            success = True
    if success:
        tkmb.show_info(title="成功", message="设置已保存")
    else:
        tkmb.show_warning(title="提示", message="设置已保存，但修改开机自启请以管理员运行！")

    load_table_data()

def load_settings():
    config = read_config()
    default = config['DEFAULT']

    room_ids = default.get('roomID', '')
    room_id_entry.delete('1.0', tk.END)
    room_id_entry.insert('1.0', room_ids)

    time_interval_var.set(default.get('timeInterval', '60'))

    loaded_api = default.get('api', 'https://api.live.bilibili.com/room/v1/Room/room_init?id=')
    api_var.set(loaded_api)

    if default.get('detectionAction'):
        old_action = default.get('detectionAction', '弹窗')
        popup_notify_var.set(old_action != '自动跳转')
        auto_jump_var.set(old_action == '自动跳转')
    else:
        popup_notify_var.set(default.getboolean('popupNotify', fallback=True))
        auto_jump_var.set(default.getboolean('autoJump', fallback=False))

    auto_listen_var.set(default.getboolean('autoStartListen', fallback=False))

    autostart_config = default.getboolean('autoStart', fallback=False)
    autostart_var.set(is_autostart_enabled() if not autostart_config else autostart_config)

def update_table_row(rid, uname, status):
    for item_id in tree.get_children():
        values = tree.item(item_id, 'values')
        if values and values[1] == rid:
            tree.item(item_id, values=(uname, rid, status))
            for i, row in enumerate(state.rowdata):
                if row[1] == rid:
                    state.rowdata[i] = (uname, rid, status)
                    break
            return
    tree.insert("", "end", values=(uname, rid, status))
    state.rowdata.append((uname, rid, status))

def load_table_data():
    room_id_text = room_id_entry.get('1.0', tk.END).strip().replace('，', ',').replace('\n', '').replace(' ', '')
    for item in tree.get_children():
        tree.delete(item)

    state.rowdata.clear()
    state.streamer_info.clear()

    if not room_id_text:
        return

    for rid in room_id_text.split(','):
        if rid and rid.isdigit():
            try:
                live_rtn = get_live_status(rid)
                uinfo_rtn = get_streamer_info(live_rtn['uid'])
                live_stat = '直播中' if live_rtn['live_status'] == 1 else '未开播'
                state.rowdata.append((uinfo_rtn['uname'], rid, live_stat))
                state.streamer_info[rid] = {'uid': live_rtn['uid'], 'uname': uinfo_rtn['uname'], 'face': uinfo_rtn['face']}
            except Exception:
                state.rowdata.append(("获取失败", rid, "错误"))

    for row in state.rowdata:
        tree.insert("", "end", values=row)

def on_exit():
    root.withdraw()

def delayed_startup():
    should_minimize = is_minimized_start()
    config = read_config()
    should_auto_listen = config.getboolean('DEFAULT', 'autoStartListen', fallback=False)
    if should_auto_listen:
        auto_listen_var.set(True)
    if should_minimize:
        root.after(500, root.withdraw)
    if should_auto_listen:
        root.after(1500, begin_listen)

def create_labeled_frame(parent, text):
    frame = ttk.Frame(parent)
    title_label = ttk.Label(frame, text=text, font=("微软雅黑", 9, "bold"))
    title_label.pack(anchor="w", padx=6, pady=(4, 2))
    separator = ttk.Separator(frame, orient="horizontal")
    separator.pack(fill=tk.X, padx=0, pady=(0, 2))
    content = ttk.Frame(frame)
    content.pack(fill=tk.BOTH, expand=True, padx=2, pady=2)
    return frame, content

if __name__ == "__main__":
    # 高DPI
    try:
        import ctypes
        ctypes.windll.shcore.SetProcessDpiAwareness(2)
    except Exception:
        try:
            ctypes.windll.user32.SetProcessDPIAware()
        except Exception:
            pass

    if not check_single_instance():
        sys.exit(0)

    root = tk.Tk()
    state = AppState()

    # DPI缩放
    try:
        dpi = root.winfo_fpixels('1i')
        root.tk.call('tk', 'scaling', dpi / 72.0)
    except Exception:
        pass

    # Win11圆角窗口
    try:
        hwnd = ctypes.windll.user32.GetParent(root.winfo_id())
        DWMWA_WINDOW_CORNER_PREFERENCE = 33
        DWM_WINDOW_CORNER_ROUND = 2
        ctypes.windll.dwmapi.DwmSetWindowAttribute(
            hwnd, DWMWA_WINDOW_CORNER_PREFERENCE,
            ctypes.byref(ctypes.c_int(DWM_WINDOW_CORNER_ROUND)),
            ctypes.sizeof(ctypes.c_int))
    except Exception:
        pass

    root.title(f'Re：B站开播提醒')
    
    try:
        dpi = root.winfo_fpixels('1i')
        scale_factor = dpi / 96.0
    except Exception:
        scale_factor = 1.0
    
    window_width = int(360 * scale_factor)
    window_height = int(540 * scale_factor)
    
    ws = root.winfo_screenwidth()
    hs = root.winfo_screenheight()
    x = (ws/2) - (window_width/2) - 20
    y = (hs/2) - (window_height/2) - 20
    root.geometry('%dx%d+%d+%d' % (window_width, window_height, x, y))
    
    root.resizable(False, False)
    
    # 图标
    from icon_module import create_android_icon
    icon_image = create_android_icon()
    try:
        root.iconphoto(True, ImageTk.PhotoImage(icon_image))
    except Exception:
        pass
    
    # grid布局
    main_control_frame = ttk.Frame(root)
    main_control_frame.pack(fill=tk.X, padx=6, pady=6)
    
    main_control_frame.grid_columnconfigure(0, weight=1, uniform='col')
    main_control_frame.grid_columnconfigure(1, weight=1, uniform='col')
    main_control_frame.grid_columnconfigure(2, weight=1, uniform='col')
    main_control_frame.grid_columnconfigure(3, weight=1, uniform='col')
    
    # 第一行
    listen = ttk.Button(main_control_frame, text='开始检测',
                        command=listen_thread, bootstyle="outline", width=8)
    listen.grid(row=0, column=0, padx=(0, 1), sticky="ew")
    
    stopl = ttk.Button(main_control_frame, text='停止检测',
                       command=stop_listen, state="disabled", bootstyle="outline", width=8)
    stopl.grid(row=0, column=1, padx=1, sticky="ew")
    
    save_btn = ttk.Button(main_control_frame, text='保存设置',
                          command=save_settings, bootstyle="outline", width=8)
    save_btn.grid(row=0, column=2, padx=1, sticky="ew")
    
    minimize_btn = ttk.Button(main_control_frame, text='回到托盘',
                             command=on_exit, bootstyle="outline", width=8)
    minimize_btn.grid(row=0, column=3, padx=(1, 0), sticky="ew")
    
    # 第二行
    state_container = ttk.Frame(main_control_frame)
    state_container.grid(row=1, column=0, padx=(0, 1), pady=(4, 0), sticky="ew")
    state_container.grid_columnconfigure(0, weight=1)
    
    stateStr = tk.StringVar(value="状态：空闲中")
    state_label = tk.Label(state_container, textvariable=stateStr, font=("微软雅黑", 9))
    state_label.grid(row=0, column=0, sticky="w")
    
    autostart_container = ttk.Frame(main_control_frame)
    autostart_container.grid(row=1, column=2, padx=1, pady=(4, 0), sticky="ew")
    autostart_container.grid_columnconfigure(0, weight=1)
    
    autostart_var = tk.BooleanVar(value=is_autostart_enabled())
    autostart_check = ttk.Checkbutton(autostart_container, text="开机自启", 
                                       variable=autostart_var)
    autostart_check.grid(row=0, column=0)
    
    auto_listen_container = ttk.Frame(main_control_frame)
    auto_listen_container.grid(row=1, column=3, padx=(1, 0), pady=(4, 0), sticky="ew")
    auto_listen_container.grid_columnconfigure(0, weight=1)
    
    auto_listen_var = tk.BooleanVar(value=False)
    auto_listen_check = ttk.Checkbutton(auto_listen_container, text="自动检测", 
                                         variable=auto_listen_var)
    auto_listen_check.grid(row=0, column=0)
    
    interval_container = ttk.Frame(main_control_frame)
    interval_container.grid(row=2, column=0, padx=(0, 1), pady=(4, 0), sticky="ew")
    interval_container.grid_columnconfigure(0, weight=1)
    
    interval_inner = ttk.Frame(interval_container)
    interval_inner.grid(row=0, column=0, sticky="w")
    
    time_interval_var = tk.StringVar(value="60")
    ttk.Label(interval_inner, text="间隔(s):").pack(side=tk.LEFT, padx=(0, 2))
    time_interval_entry = ttk.Entry(interval_inner, textvariable=time_interval_var, 
                                   width=6, justify='center')
    time_interval_entry.pack(side=tk.LEFT)
    

    detection_action_label_container = ttk.Frame(main_control_frame)
    detection_action_label_container.grid(row=2, column=1, padx=1, pady=(4, 0), sticky="ew")
    detection_action_label_container.grid_columnconfigure(0, weight=1)
    
    ttk.Label(detection_action_label_container, text="检测操作:").grid(row=0, column=0, sticky="e")
    
    popup_notify_container = ttk.Frame(main_control_frame)
    popup_notify_container.grid(row=2, column=2, padx=1, pady=(4, 0), sticky="ew")
    popup_notify_container.grid_columnconfigure(0, weight=1)
    
    popup_notify_var = tk.BooleanVar(value=True)
    popup_notify_check = ttk.Checkbutton(popup_notify_container, text="弹窗提醒",
                                         variable=popup_notify_var)
    popup_notify_check.grid(row=0, column=0)
    
    auto_jump_container = ttk.Frame(main_control_frame)
    auto_jump_container.grid(row=2, column=3, padx=(1, 0), pady=(4, 0), sticky="ew")
    auto_jump_container.grid_columnconfigure(0, weight=1)
    
    auto_jump_var = tk.BooleanVar(value=False)
    auto_jump_check = ttk.Checkbutton(auto_jump_container, text="自动跳转",
                                      variable=auto_jump_var)
    auto_jump_check.grid(row=0, column=0)
    
    room_settings_frame, room_settings_inner = create_labeled_frame(root, "检测设置")
    room_settings_frame.pack(fill=tk.X, padx=6, pady=(0, 6))
    
    room_id_frame = ttk.Frame(room_settings_inner)
    room_id_frame.pack(fill=tk.X)
    
    room_id_label = ttk.Label(room_id_frame, text="房间号(逗号隔开):")
    room_id_label.pack(side=tk.LEFT, anchor="w")
    
    room_id_entry = tk.Text(room_id_frame, height=1, width=25, relief=tk.SUNKEN, 
                            font=("微软雅黑", 9), wrap=tk.NONE)
    room_id_entry.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(5, 0))
    
    # API设置
    api_frame = ttk.Frame(room_settings_inner)
    api_frame.pack(fill=tk.X, pady=(6, 0))
    
    api_label = ttk.Label(api_frame, text="API:")
    api_label.pack(side=tk.LEFT, anchor="w")
    
    api_var = tk.StringVar(value="https://api.live.bilibili.com/room/v1/Room/room_init?id=")
    api_entry = ttk.Entry(api_frame, textvariable=api_var, font=("微软雅黑", 9))
    api_entry.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(5, 5))
    
    def restore_default_api():
        api_var.set("https://api.live.bilibili.com/room/v1/Room/room_init?id=")
        log("已恢复默认API")
    
    restore_api_btn = ttk.Button(api_frame, text="默认", width=5,
                                command=restore_default_api, bootstyle="secondary-outline")
    restore_api_btn.pack(side=tk.RIGHT)
    
    # 直播间状态
    table_frame, table_inner = create_labeled_frame(root, "直播间状态")
    table_frame.pack(fill=tk.BOTH, expand=True, padx=6, pady=(0, 6))
    
    table_container = ttk.Frame(table_inner)
    table_container.pack(fill=tk.BOTH, expand=True)
    
    table_scrollbar = ttk.Scrollbar(table_container, bootstyle="round")
    table_scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
    
    tree_frame = tk.Frame(table_container, bd=1, relief="solid")
    tree_frame.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
    
    tree = ttk.Treeview(
        tree_frame,
        columns=('主播', '房间号', '直播状态'),
        show='headings',
        height=5,
        yscrollcommand=table_scrollbar.set,
        selectmode='browse',
        takefocus=False
    )
    
    def _update_tree_col_widths(event=None):
        total = table_frame.winfo_width() - 20
        if total > 60:
            w = total // 3
            tree.column('主播', width=w, minwidth=80)
            tree.column('房间号', width=w, minwidth=80)
            tree.column('直播状态', width=w, minwidth=80)

    table_frame.bind('<Configure>', _update_tree_col_widths)
    tree.column('主播', anchor='center')
    tree.column('房间号', anchor='center')
    tree.column('直播状态', anchor='center')
    
    tree.heading('主播', text='主播', anchor='center')
    tree.heading('房间号', text='房间号', anchor='center')
    tree.heading('直播状态', text='直播状态', anchor='center')
    
    style = ttk.Style()
    style.configure("Treeview", 
                    rowheight=25)
    tree_bg = style.lookup('Treeview', 'background') or '#ffffff'
    tree_fg = style.lookup('Treeview', 'foreground') or '#000000'
    style.map('Treeview', 
              background=[('selected', tree_bg), ('hover', tree_bg)],
              foreground=[('selected', tree_fg), ('hover', tree_fg)])
    
    def block_tree_interaction(event):
        region = tree.identify_region(event.x, event.y)
        if region == "separator":
            return "break"
    
    tree.bind('<Button-1>', block_tree_interaction)
    tree.bind('<B1-Motion>', block_tree_interaction)
    
    def copy_tree_selection():
        selected = tree.selection()
        if selected:
            values = tree.item(selected[0], 'values')
            if values:
                root.clipboard_clear()
                text = '\t'.join(str(v) for v in values)
                root.clipboard_append(text)
    
    tree_menu = tk.Menu(root, tearoff=0)
    tree_menu.add_command(label="复制", command=copy_tree_selection)
    
    def show_tree_menu(event):
        tree_menu.tk_popup(event.x_root, event.y_root)
        return "break"
    
    tree.bind('<Button-3>', show_tree_menu)
    tree.bind('<Control-c>', lambda e: [copy_tree_selection(), "break"])
    tree.bind('<Control-C>', lambda e: [copy_tree_selection(), "break"])
    
    tree.pack(fill=tk.BOTH, expand=True)
    
    table_scrollbar.config(command=tree.yview)
    
    tree.configure(xscrollcommand=None)
    
    # 运行日志
    log_frame, log_inner = create_labeled_frame(root, "运行日志")
    log_frame.pack(fill=tk.X, padx=6, pady=(0, 8))
    
    info_scr = ttk.Scrollbar(log_inner, bootstyle="round")
    info_text = tk.Text(log_inner, height=8, bd=1,
                        font=tkfont.Font(family="Microsoft YaHei", size=9),
                        yscrollcommand=info_scr.set, state=tk.DISABLED,
                        wrap=tk.WORD)
    info_scr.config(command=info_text.yview)
    info_scr.pack(side=tk.RIGHT, fill=tk.Y)
    info_text.pack(side=tk.LEFT, fill=tk.BOTH)
    
    if not os.path.exists(config_path):
        config = configparser.ConfigParser()
        config['DEFAULT'] = {'api': 'https://api.live.bilibili.com/room/v1/Room/room_init?id=',
                             'roomID': '', 'timeInterval': '60', 'autoJump': '0',
                             'autoStartListen': '0', 'autoStart': '0'}
        write_config(config)
    
    load_settings()
    load_table_data()
    
    # 托盘菜单
    menu = (MenuItem('显示', show_window, default=True), 
            MenuItem('检查更新', check_for_updates),
            Menu.SEPARATOR, 
            MenuItem('关于', show_about_window),
            Menu.SEPARATOR, 
            MenuItem('退出', quit_window))
    icon = pystray.Icon("bili_live_notification", icon_image, "Re：B站开播提醒", menu)
    
    root.protocol('WM_DELETE_WINDOW', quit_window)
    threading.Thread(target=icon.run, name="stray", daemon=True).start()
    root.after(100, delayed_startup)
    
    root.mainloop()
