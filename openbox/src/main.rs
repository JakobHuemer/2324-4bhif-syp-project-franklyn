use iced::{
    alignment,
    widget::{button, center, column, container, row, text, text_input},
    Center, Element, Subscription, Task, Theme,
};
use openbox::ws::Event;

const _PROD_URL: &str = "franklyn3.htl-leonding.ac.at:8080";
const _DEV_URL: &str = "localhost:8080";

#[derive(Debug, Clone)]
enum Message {
    PinChanged(String),
    FirstnameChanged(String),
    LastnameChanged(String),

    Connect,
    FocusNext,

    Ev(openbox::ws::Event),
}

struct Openbox<'a> {
    pin: String,

    firstname: String,
    lastname: String,

    server_address: &'a str,
    should_connect: bool,
}

impl<'a> Openbox<'a> {
    fn new() -> (Self, Task<Message>) {
        (
            Self {
                pin: String::new(),
                firstname: String::new(),
                lastname: String::new(),

                server_address: _DEV_URL,
                should_connect: false,
            },
            Task::none(),
        )
    }

    fn update(&mut self, message: Message) -> Task<Message> {
        match message {
            Message::PinChanged(pin) => self.pin = pin,
            Message::FirstnameChanged(firstname) => self.firstname = firstname,
            Message::LastnameChanged(lastname) => self.lastname = lastname,
            Message::Ev(Event::Disconnect) => return iced::exit(),
            Message::Connect => self.should_connect = true, 
            _ => (),
        }

        Task::none()
    }

    fn subscription(&self) -> Subscription<Message> {
        use iced::keyboard;
        use openbox::ws;

        let hotkeys = keyboard::on_key_press(|key, _mod| match key {
            keyboard::Key::Named(keyboard::key::Named::Tab) => Some(Message::FocusNext),
            _ => None,
        });

        let ws = if self.should_connect {
            ws::subscribe(
                self.pin.clone(),
                self.server_address.to_string(),
                self.firstname.clone(),
                self.lastname.clone(),
            )
            .map(Message::Ev)
        } else { Subscription::none() };

        Subscription::batch([hotkeys, ws])
    }

    fn view(&self) -> Element<Message> {
        let logo = row![
            container(text("FRAN").size(70))
                .style(|_| openbox::theme::LogoTheme::default().to_style()),
            text("KLYN").size(70),
        ]
        .align_y(Center);

        center(if !self.should_connect {
            let pin_input = text_input("pin", &self.pin)
                .on_input(Message::PinChanged)
                .width(300)
                .padding(10)
                .id(iced::widget::text_input::Id::new("pin"));

            let firstname_input = text_input("firstname", &self.firstname)
                .on_input(Message::FirstnameChanged)
                .width(300)
                .padding(10)
                .id(iced::widget::text_input::Id::new("firstname"));

            let lastname_input = text_input("lastname", &self.lastname)
                .on_input(Message::LastnameChanged)
                .width(300)
                .padding(10)
                .id(iced::widget::text_input::Id::new("lastname"));

            let mut button = button(
                text("connect")
                    .height(40)
                    .align_y(alignment::Vertical::Center)
                    .align_x(alignment::Horizontal::Center),
            )
            .width(300)
            .padding([0, 20]);

            let is_valid_range = 2..=50;

            if self.pin.parse::<u16>().is_ok()
                && self.pin.len() == 3
                && is_valid_range.contains(&self.firstname.len())
                && is_valid_range.contains(&self.lastname.len())
            {
                button = button.on_press(Message::Connect);
            }

            column![
                column![logo].padding([50, 50]),
                pin_input,
                firstname_input,
                lastname_input,
                button
            ]
            .spacing(10)
            .align_x(Center)
        } else {
            column![
                logo,
                row![
                    text(&self.firstname).size(50),
                    text(&self.lastname).size(50)
                ]
                .spacing(20)
            ]
            .spacing(20)
            .align_x(Center)
        })
        .into()
    }

    fn theme(&self) -> Theme {
        Theme::default()
    }
}

pub fn main() -> iced::Result {
    iced::application("Openbox", Openbox::update, Openbox::view)
        .theme(Openbox::theme)
        .subscription(Openbox::subscription)
        .run_with(Openbox::new)
}
