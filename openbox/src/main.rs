use iced::{
    alignment,
    widget::{button, center, column, container, row, text, text_input},
    Center, Element, Subscription, Task, Theme,
};
use image::RgbaImage;
use openbox::ws::Event;

const _PROD_URL: &str = "franklyn3.htl-leonding.ac.at:8080";
const _DEV_URL: &str = "localhost:8080";

#[derive(Debug, Clone)]
enum Message {
    PinChanged(String),
    FirstnameChanged(String),
    LastnameChanged(String),

    Ev(openbox::ws::Event),
    Connect(String),

    FocusNext,
}

#[derive(PartialEq)]
enum ConnectionState {
    Idle,
    Connected,
    Reconnecting(String),
    Disconnected,
}

struct Openbox<'a> {
    pin: String,
    firstname: String,
    lastname: String,

    connection: ConnectionState,
    server_address: &'a str,
    old_image: Option<RgbaImage>,
}

impl<'a> Openbox<'a> {
    fn new() -> (Self, Task<Message>) {
        (
            Self {
                pin: String::new(),
                firstname: String::new(),
                lastname: String::new(),

                connection: ConnectionState::Idle,
                server_address: _DEV_URL,
                old_image: None,
            },
            Task::none(),
        )
    }

    fn update(&mut self, message: Message) -> Task<Message> {
        match message {
            Message::PinChanged(pin) => {
                self.pin = pin;
                Task::none()
            }
            Message::FirstnameChanged(firstname) => {
                self.firstname = firstname;
                Task::none()
            }
            Message::LastnameChanged(lastname) => {
                self.lastname = lastname;
                Task::none()
            }
            Message::Ev(Event::UpdateImage(new_image)) => {
                self.old_image = Some(new_image);
                Task::none()
            }
            Message::Connect(pin) => {
                self.connection = ConnectionState::Reconnecting(pin);
                Task::none()
            }
            _ => Task::none(),
        }
    }

    fn subscription(&self) -> Subscription<Message> {
        use iced::keyboard;
        use openbox::ws;

        let hotkeys = keyboard::on_key_press(|key, _mod| match key {
            keyboard::Key::Named(keyboard::key::Named::Tab) => Some(Message::FocusNext),
            _ => None,
        });

        let ws = match &self.connection {
            ConnectionState::Reconnecting(pin) => ws::subscribe(
                pin.clone(), 
                self.firstname.clone(),
                self.lastname.clone(),
                self.server_address.to_string(),
                self.old_image.clone(),
            )
            .map(Message::Ev),
            ConnectionState::Idle => Subscription::none(),
            ConnectionState::Connected => Subscription::none(),
            ConnectionState::Disconnected => Subscription::none(),
        };

        Subscription::batch([hotkeys, ws])
    }

    fn view(&self) -> Element<Message> {
        let logo = row![
            container(text("FRAN").size(70))
                .style(|_| openbox::theme::LogoTheme::default().to_style()),
            text("KLYN").size(70),
        ]
        .align_y(Center);

        center(if self.connection == ConnectionState::Idle {
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

            if self.pin.parse::<u16>().is_ok()
                && self.pin.len() == 3
                && !self.firstname.is_empty()
                && !self.lastname.is_empty()
            {
                button = button.on_press(Message::Connect(self.pin.clone()));
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
